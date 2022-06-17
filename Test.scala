import EnergyCheckUtils._

object Test {

// --- Main method to test our native library
    def main(args: Array[String]): Unit = {
        val sample = new EnergyCheckUtils
        EnergyCheckUtils.getEnergyStats()
    }
}
