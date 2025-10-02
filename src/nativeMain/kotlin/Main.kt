import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
fun main() {
    val windowTitle = "Sledding Game Demo"

    println("Searching for process: $windowTitle")
    val pid = ProcessMemory.findProcessByTitle(windowTitle)

    if (pid == null) {
        println("Process not found. Make sure the game is running.")
        return
    }

    println("Found process with PID: $pid")

    val handle = ProcessMemory.openProcess(pid)
    if (handle == null) {
        println("Failed to open process. Try running as administrator.")
        return
    }

    println("Successfully opened process handle")

    // Get initial money value from user
    print("\nEnter your current money amount: ")
    val initialMoney = readln().toIntOrNull()
    if (initialMoney == null) {
        println("Invalid number entered.")
        ProcessMemory.closeHandle(handle)
        return
    }

    // First scan
    println("\nGetting memory regions...")
    val regions = ProcessMemory.getMemoryRegions(handle, pid)
    println("Found ${regions.size} readable memory regions")

    println("Scanning for value: $initialMoney")
    var candidates = mutableListOf<ULong>()
    var scannedRegions = 0

    for ((baseAddress, size) in regions) {
        val matches = ProcessMemory.scanForInt(handle, baseAddress, baseAddress + size, initialMoney)
        if (matches.isNotEmpty()) {
            candidates.addAll(matches)
            println("Found ${matches.size} matches in region 0x${baseAddress.toString(16)}")
        }
        scannedRegions++
        if (scannedRegions % 100 == 0) {
            println("Scanned $scannedRegions/${regions.size} regions...")
        }
    }

    println("\nFirst scan complete! Found ${candidates.size} potential addresses")

    if (candidates.isEmpty()) {
        println("No matches found. Make sure you entered the correct amount.")
        ProcessMemory.closeHandle(handle)
        return
    }

    // Prompt for second scan
    println("\nNow buy or sell something to change your money amount.")
    print("Enter your new money amount: ")
    val secondMoney = readln().toIntOrNull()
    if (secondMoney == null) {
        println("Invalid number entered.")
        ProcessMemory.closeHandle(handle)
        return
    }

    // Second scan - filter candidates
    println("\nRe-scanning ${candidates.size} addresses for new value: $secondMoney")
    candidates = ProcessMemory.rescanAddresses(handle, candidates, secondMoney).toMutableList()

    println("Second scan complete! Found ${candidates.size} matching addresses")

    if (candidates.isEmpty()) {
        println("No matches found after second scan. The value may not be stored as a simple integer.")
        ProcessMemory.closeHandle(handle)
        return
    }

    println("\nFinal addresses:")
    candidates.forEach { address ->
        println("  0x${address.toString(16)}")
    }

    // Get desired money value and write
    print("\nEnter the amount of money you want: ")
    val desiredMoney = readln().toIntOrNull()
    if (desiredMoney == null) {
        println("Invalid number entered.")
        ProcessMemory.closeHandle(handle)
        return
    }

    println("\nWriting $desiredMoney to ${candidates.size} addresses...")
    val written = ProcessMemory.writeIntToMultiple(handle, candidates, desiredMoney)
    println("Successfully wrote to $written/${candidates.size} addresses")
    println("\nMemory updated! The in-game display will update after you spend or earn money.")
    println("(Buy/sell something)")

    ProcessMemory.closeHandle(handle)

    print("\nPress Enter to exit...")
    readln()
}
