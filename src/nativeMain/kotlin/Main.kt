import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Read a line of input, trim whitespace, and parse as an integer.
 * @return The parsed integer, or null if invalid.
 */
private fun readInt(): Int? {
    return readln().trim().toIntOrNull()
}

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

    // Get initial values from user
    print("\nEnter your current money amount: ")
    val initialMoney = readInt()
    if (initialMoney == null) {
        println("Invalid number entered.")
        ProcessMemory.closeHandle(handle)
        return
    }

    print("Enter your current ticket amount: ")
    val initialTickets = readInt()
    if (initialTickets == null) {
        println("Invalid number entered.")
        ProcessMemory.closeHandle(handle)
        return
    }

    // First scan
    println("\nGetting memory regions...")
    val regions = ProcessMemory.getMemoryRegions(handle, pid)
    println("Found ${regions.size} readable memory regions")

    println("Scanning for money: $initialMoney and tickets: $initialTickets")
    var moneyCandidates = mutableListOf<ULong>()
    var ticketCandidates = mutableListOf<ULong>()
    var scannedRegions = 0

    for ((baseAddress, size) in regions) {
        val moneyMatches = ProcessMemory.scanForInt(handle, baseAddress, baseAddress + size, initialMoney)
        val ticketMatches = ProcessMemory.scanForInt(handle, baseAddress, baseAddress + size, initialTickets)

        if (moneyMatches.isNotEmpty()) {
            moneyCandidates.addAll(moneyMatches)
        }
        if (ticketMatches.isNotEmpty()) {
            ticketCandidates.addAll(ticketMatches)
        }

        scannedRegions++
        if (scannedRegions % 100 == 0) {
            println("Scanned $scannedRegions/${regions.size} regions...")
        }
    }

    println("\nFirst scan complete!")
    println("  Money: ${moneyCandidates.size} potential addresses")
    println("  Tickets: ${ticketCandidates.size} potential addresses")

    if (moneyCandidates.isEmpty() && ticketCandidates.isEmpty()) {
        println("No matches found. Make sure you entered the correct amounts.")
        ProcessMemory.closeHandle(handle)
        return
    }

    // Prompt for second scan
    println("\nNow buy or sell something to change your money and ticket amounts.")

    print("Enter your new money amount: ")
    val secondMoney = readInt()
    if (secondMoney == null) {
        println("Invalid number entered.")
        ProcessMemory.closeHandle(handle)
        return
    }

    print("Enter your new ticket amount: ")
    val secondTickets = readInt()
    if (secondTickets == null) {
        println("Invalid number entered.")
        ProcessMemory.closeHandle(handle)
        return
    }

    // Second scan - filter candidates
    println("\nRe-scanning addresses...")

    if (moneyCandidates.isNotEmpty()) {
        println("  Checking ${moneyCandidates.size} money addresses for value: $secondMoney")
        moneyCandidates = ProcessMemory.rescanAddresses(handle, moneyCandidates, secondMoney).toMutableList()
    }

    if (ticketCandidates.isNotEmpty()) {
        println("  Checking ${ticketCandidates.size} ticket addresses for value: $secondTickets")
        ticketCandidates = ProcessMemory.rescanAddresses(handle, ticketCandidates, secondTickets).toMutableList()
    }

    println("\nSecond scan complete!")
    println("  Money: ${moneyCandidates.size} matching addresses")
    println("  Tickets: ${ticketCandidates.size} matching addresses")

    if (moneyCandidates.isEmpty() && ticketCandidates.isEmpty()) {
        println("No matches found after second scan. The values may not be stored as simple integers.")
        ProcessMemory.closeHandle(handle)
        return
    }

    // Show final addresses
    if (moneyCandidates.isNotEmpty()) {
        println("\nMoney addresses:")
        moneyCandidates.forEach { address ->
            println("  0x${address.toString(16)}")
        }
    }

    if (ticketCandidates.isNotEmpty()) {
        println("\nTicket addresses:")
        ticketCandidates.forEach { address ->
            println("  0x${address.toString(16)}")
        }
    }

    // Get desired values and write
    if (moneyCandidates.isNotEmpty()) {
        print("\nEnter the amount of money you want: ")
        val desiredMoney = readInt()
        if (desiredMoney == null) {
            println("Invalid number entered.")
            ProcessMemory.closeHandle(handle)
            return
        }

        println("Writing $desiredMoney to ${moneyCandidates.size} money addresses...")
        val written = ProcessMemory.writeIntToMultiple(handle, moneyCandidates, desiredMoney)
        println("Successfully wrote to $written/${moneyCandidates.size} addresses")
    }

    if (ticketCandidates.isNotEmpty()) {
        print("\nEnter the amount of tickets you want: ")
        val desiredTickets = readInt()
        if (desiredTickets == null) {
            println("Invalid number entered.")
            ProcessMemory.closeHandle(handle)
            return
        }

        println("Writing $desiredTickets to ${ticketCandidates.size} ticket addresses...")
        val written = ProcessMemory.writeIntToMultiple(handle, ticketCandidates, desiredTickets)
        println("Successfully wrote to $written/${ticketCandidates.size} addresses")
    }

    println("\nMemory updated! The in-game display will update after you spend or earn money/tickets.")
    println("(Buy/sell something)")

    ProcessMemory.closeHandle(handle)

    print("\nPress Enter to exit...")
    readln()
}
