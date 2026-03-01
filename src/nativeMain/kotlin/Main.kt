import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.sleep

/**
 * Read an integer from the user with retry logic.
 * Keeps prompting until a valid integer is entered.
 *
 * @param prompt The prompt to display
 * @return The parsed integer
 */
private fun readIntWithRetry(prompt: String): Int {
    while (true) {
        print(prompt)
        val input = readln().trim()

        // Check if it's a valid number format
        val asLong = input.toLongOrNull()
        if (asLong == null) {
            println("That doesn't look like a number. Try again.")
            continue
        }

        // Check if it's within Int range (game likely uses 32-bit integers)
        if (asLong > Int.MAX_VALUE || asLong < Int.MIN_VALUE) {
            println("That's outside the acceptable range. Try another number. (0-${Int.MAX_VALUE})")
            continue
        }

        return asLong.toInt()
    }
}

@OptIn(ExperimentalForeignApi::class)
fun main() {
    val windowTitle = "Sledding Game Demo"

    println("Looking for Sledding Game Demo...")

    var pid = ProcessMemory.findProcessByTitle(windowTitle)
    while (pid == null) {
        println("Game not found. Launch the game and get into a lobby.")
        print("Waiting for game")
        repeat(5) {
            sleep(1u)
            print(".")
        }
        println()
        pid = ProcessMemory.findProcessByTitle(windowTitle)
    }

    println("Found the game!")

    val handle = ProcessMemory.openProcess(pid)
    if (handle == null) {
        println("Couldn't connect to the game. Try running FrostyFunds as administrator.")
        print("\nPress Enter to exit...")
        readln()
        return
    }

    println("Connected successfully!")

    // Get initial values from user
    println()
    val initialMoney = readIntWithRetry("Enter your current money amount: ")
    val initialTickets = readIntWithRetry("Enter your current ticket amount: ")

    // First scan
    val regions = ProcessMemory.getMemoryRegions(handle)
    var moneyCandidates = mutableListOf<ULong>()
    var ticketCandidates = mutableListOf<ULong>()

    val scanProgress = ProgressBar("Scanning", regions.size)
    for ((index, region) in regions.withIndex()) {
        val (baseAddress, size) = region
        val moneyMatches = ProcessMemory.scanForInt(handle, baseAddress, baseAddress + size, initialMoney)
        val ticketMatches = ProcessMemory.scanForInt(handle, baseAddress, baseAddress + size, initialTickets)

        if (moneyMatches.isNotEmpty()) {
            moneyCandidates.addAll(moneyMatches)
        }
        if (ticketMatches.isNotEmpty()) {
            ticketCandidates.addAll(ticketMatches)
        }

        scanProgress.update(index + 1)
    }
    scanProgress.complete()

    println("\nFirst scan complete!")
    println("  Money: ${moneyCandidates.size} potential addresses")
    println("  Tickets: ${ticketCandidates.size} potential addresses")

    if (moneyCandidates.isEmpty() && ticketCandidates.isEmpty()) {
        println("No matches found. Make sure you entered the correct amounts.")
        ProcessMemory.closeHandle(handle)
        print("\nPress Enter to exit...")
        readln()
        return
    }

    // Prompt for second scan
    println("\nNow buy or sell something to change your money and ticket amounts.")

    // Track whether we had candidates from the first scan
    val hadMoneyCandidates = moneyCandidates.isNotEmpty()
    val hadTicketCandidates = ticketCandidates.isNotEmpty()

    // Second scan with retry logic for each type
    if (hadMoneyCandidates) {
        while (true) {
            val secondMoney = readIntWithRetry("Enter your new money amount: ")
            val filtered = mutableListOf<ULong>()
            for (address in moneyCandidates) {
                if (ProcessMemory.readInt(handle, address) == secondMoney) {
                    filtered.add(address)
                }
            }
            if (filtered.isNotEmpty()) {
                moneyCandidates = filtered
                println("  Found ${moneyCandidates.size} matching money addresses")
                break
            }
            println("No matches found. Check the amount and try again.")
        }
    }

    if (hadTicketCandidates) {
        while (true) {
            val secondTickets = readIntWithRetry("Enter your new ticket amount: ")
            val filtered = mutableListOf<ULong>()
            for (address in ticketCandidates) {
                if (ProcessMemory.readInt(handle, address) == secondTickets) {
                    filtered.add(address)
                }
            }
            if (filtered.isNotEmpty()) {
                ticketCandidates = filtered
                println("  Found ${ticketCandidates.size} matching ticket addresses")
                break
            }
            println("No matches found. Check the amount and try again.")
        }
    }

    println("\nSecond scan complete!")

    // Get desired values and write
    if (moneyCandidates.isNotEmpty()) {
        val desiredMoney = readIntWithRetry("\nEnter the amount of money you want: ")
        println("Writing $desiredMoney to ${moneyCandidates.size} money addresses...")
        val written = ProcessMemory.writeIntToMultiple(handle, moneyCandidates, desiredMoney)
        println("Successfully wrote to $written/${moneyCandidates.size} addresses")
    }

    if (ticketCandidates.isNotEmpty()) {
        val desiredTickets = readIntWithRetry("\nEnter the amount of tickets you want: ")
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
