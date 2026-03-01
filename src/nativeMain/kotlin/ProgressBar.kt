/**
 * Simple progress bar for CLI output using carriage return for in-place updates.
 *
 * @param label The label to display before the progress bar
 * @param total The total number of items to process
 * @param width The width of the progress bar in characters (default 20)
 */
class ProgressBar(
    private val label: String,
    private val total: Int,
    private val width: Int = 20
) {
    private var current = 0

    /**
     * Update the progress bar to a new value.
     * @param value The current progress value (0 to total)
     */
    fun update(value: Int) {
        current = value.coerceIn(0, total)
        render()
    }

    private fun render() {
        val percentage = if (total > 0) (current * 100) / total else 100
        val filledWidth = if (total > 0) (current * width) / total else width
        val emptyWidth = width - filledWidth

        val bar = "█".repeat(filledWidth) + "░".repeat(emptyWidth)
        val output = "\r$label: [$bar] $percentage% ($current/$total)"

        print(output)
    }

    /**
     * Complete the progress bar, moving to a new line.
     */
    fun complete() {
        current = total
        render()
        println()
    }
}
