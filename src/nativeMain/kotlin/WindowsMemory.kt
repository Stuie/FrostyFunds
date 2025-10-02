@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.windows.*

object ProcessMemory {
    /**
     * Find a process by window title
     * @param windowTitle The window title to search for
     * @return Process ID if found, null otherwise
     */
    fun findProcessByTitle(windowTitle: String): UInt? {
        memScoped {
            val hwnd = FindWindowA(null, windowTitle) ?: return null

            val pidPtr = alloc<UIntVar>()
            GetWindowThreadProcessId(hwnd, pidPtr.ptr)
            val pid = pidPtr.value

            return if (pid != 0u) pid else null
        }
    }

    /**
     * Open a process handle with full access rights
     * @param processId The process ID to open
     * @return Process handle if successful, null otherwise
     */
    fun openProcess(processId: UInt): HANDLE? {
        val handle = OpenProcess(
            (PROCESS_VM_READ or PROCESS_VM_WRITE or PROCESS_VM_OPERATION).toUInt(),
            0,
            processId
        )
        return handle
    }

    /**
     * Read memory from a process
     * @param handle Process handle
     * @param address Memory address to read from
     * @param size Number of bytes to read
     * @return ByteArray containing the read data, or null on failure
     */
    fun readMemory(handle: HANDLE, address: ULong, size: Int): ByteArray? {
        return memScoped {
            val buffer = ByteArray(size)
            val bytesRead = alloc<ULongVar>()

            buffer.usePinned { pinned ->
                val result = ReadProcessMemory(
                    handle,
                    address.toLong().toCPointer(),
                    pinned.addressOf(0),
                    size.toULong(),
                    bytesRead.ptr
                )

                if (result != 0 && bytesRead.value == size.toULong()) {
                    buffer
                } else {
                    null
                }
            }
        }
    }

    /**
     * Write memory to a process
     * @param handle Process handle
     * @param address Memory address to write to
     * @param data Data to write
     * @return true if successful, false otherwise
     */
    fun writeMemory(handle: HANDLE, address: ULong, data: ByteArray): Boolean {
        return memScoped {
            val bytesWritten = alloc<ULongVar>()

            data.usePinned { pinned ->
                val result = WriteProcessMemory(
                    handle,
                    address.toLong().toCPointer(),
                    pinned.addressOf(0),
                    data.size.toULong(),
                    bytesWritten.ptr
                )

                result != 0 && bytesWritten.value == data.size.toULong()
            }
        }
    }

    /**
     * Read an integer (4 bytes) from memory
     */
    fun readInt(handle: HANDLE, address: ULong): Int? {
        val bytes = readMemory(handle, address, 4) ?: return null
        return bytes.asUByteArray().let {
            (it[0].toInt() or
             (it[1].toInt() shl 8) or
             (it[2].toInt() shl 16) or
             (it[3].toInt() shl 24))
        }
    }

    /**
     * Write an integer (4 bytes) to memory
     */
    fun writeInt(handle: HANDLE, address: ULong, value: Int): Boolean {
        val bytes = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
        return writeMemory(handle, address, bytes)
    }

    /**
     * Read a float (4 bytes) from memory
     */
    fun readFloat(handle: HANDLE, address: ULong): Float? {
        val intValue = readInt(handle, address) ?: return null
        return Float.fromBits(intValue)
    }

    /**
     * Write a float (4 bytes) to memory
     */
    fun writeFloat(handle: HANDLE, address: ULong, value: Float): Boolean {
        return writeInt(handle, address, value.toRawBits())
    }

    /**
     * Scan memory for an integer value
     * @param handle Process handle
     * @param startAddress Starting address to scan from
     * @param endAddress Ending address to scan to
     * @param value The integer value to search for
     * @param alignment Memory alignment (default 4 bytes for int)
     * @return List of addresses where the value was found
     */
    fun scanForInt(
        handle: HANDLE,
        startAddress: ULong,
        endAddress: ULong,
        value: Int,
        alignment: Int = 4,
        chunkSize: Int = 1024 * 1024 // 1MB chunks
    ): List<ULong> {
        val results = mutableListOf<ULong>()
        var currentAddress = startAddress

        while (currentAddress < endAddress) {
            val remainingBytes = (endAddress - currentAddress).coerceAtMost(chunkSize.toULong()).toInt()
            val chunk = readMemory(handle, currentAddress, remainingBytes)

            if (chunk != null) {
                // Search for the value in this chunk
                for (offset in 0..(chunk.size - 4) step alignment) {
                    val intValue = chunk.asUByteArray().let {
                        (it[offset].toInt() or
                         (it[offset + 1].toInt() shl 8) or
                         (it[offset + 2].toInt() shl 16) or
                         (it[offset + 3].toInt() shl 24))
                    }
                    if (intValue == value) {
                        results.add(currentAddress + offset.toULong())
                    }
                }
            }

            currentAddress += remainingBytes.toULong()
        }

        return results
    }

    /**
     * Get readable memory regions for a process
     * @param handle Process handle
     * @param pid Process ID
     * @return List of memory regions (start address, size)
     */
    fun getMemoryRegions(handle: HANDLE, pid: UInt): List<Pair<ULong, ULong>> {
        val regions = mutableListOf<Pair<ULong, ULong>>()
        var address = 0uL
        val maxAddress = 0x7FFFFFFFFFFFuL // Max user-mode address on 64-bit Windows

        memScoped {
            val mbi = alloc<MEMORY_BASIC_INFORMATION>()

            while (address < maxAddress) {
                val result = VirtualQueryEx(
                    handle,
                    address.toLong().toCPointer(),
                    mbi.ptr,
                    sizeOf<MEMORY_BASIC_INFORMATION>().toULong()
                )

                if (result == 0uL) break

                // Check if region is committed and readable
                if (mbi.State.toInt() == MEM_COMMIT &&
                    (mbi.Protect.toInt() == PAGE_READWRITE ||
                     mbi.Protect.toInt() == PAGE_READONLY ||
                     mbi.Protect.toInt() == PAGE_EXECUTE_READWRITE ||
                     mbi.Protect.toInt() == PAGE_EXECUTE_READ)) {
                    regions.add(Pair(mbi.BaseAddress.toLong().toULong(), mbi.RegionSize))
                }

                address = mbi.BaseAddress.toLong().toULong() + mbi.RegionSize
            }
        }

        return regions
    }

    /**
     * Re-scan specific addresses to filter out ones that no longer match
     * @param handle Process handle
     * @param addresses List of addresses from previous scan
     * @param newValue The new value to search for
     * @return Filtered list of addresses that still match
     */
    fun rescanAddresses(handle: HANDLE, addresses: List<ULong>, newValue: Int): List<ULong> {
        return addresses.filter { address ->
            readInt(handle, address) == newValue
        }
    }

    /**
     * Write the same integer value to multiple addresses
     * @param handle Process handle
     * @param addresses List of addresses to write to
     * @param value The value to write
     * @return Number of successful writes
     */
    fun writeIntToMultiple(handle: HANDLE, addresses: List<ULong>, value: Int): Int {
        var successCount = 0
        for (address in addresses) {
            if (writeInt(handle, address, value)) {
                successCount++
                println("Successfully wrote $value to 0x${address.toString(16)}")
            } else {
                println("Failed to write to 0x${address.toString(16)}")
            }
        }
        return successCount
    }

    /**
     * Close a process handle
     */
    fun closeHandle(handle: HANDLE) {
        CloseHandle(handle)
    }
}
