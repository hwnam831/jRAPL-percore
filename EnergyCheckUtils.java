import java.lang.reflect.Field;
public class EnergyCheckUtils {
	//public native static int scale(int freq);
	//public native static int[] freqAvailable();

	//public native static double[] GetPackagePowerSpec();
	//public native static double[] GetDramPowerSpec();
	//public native static void SetPackagePowerLimit(int socketId, int level, double costomPower);
	//public native static void SetPackageTimeWindowLimit(int socketId, int level, double costomTimeWin);
	//public native static void SetDramTimeWindowLimit(int socketId, int level, double costomTimeWin);
	//public native static void SetDramPowerLimit(int socketId, int level, double costomPower);
	public native static int ProfileInit();
	public native static int GetSocketNum();
	public native static String EnergyStatCheck();
	public native static void ProfileDealloc();
	//public native static void SetPowerLimit(int ENABLE);
	public static int wraparoundValue;

	public static int socketNum;
	static {
		/* 
		System.setProperty("java.library.path", System.getProperty("user.dir"));
		try {
			Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
			fieldSysPath.setAccessible(true);
			fieldSysPath.set(null, null);
		} catch (Exception e) { }
		*/
		System.loadLibrary("CPUScaler");
		wraparoundValue = ProfileInit();
		socketNum = GetSocketNum();
	}

	/**
	 * @return an array of current energy information.
	 * The first entry is: Dram/uncore gpu energy(depends on the cpu architecture.
	 * The second entry is: CPU energy
	 * The third entry is: Package energy
	 */

	public static double[] getEnergyStats() {
		socketNum = GetSocketNum();
		String EnergyInfo = EnergyStatCheck();
		System.out.println(EnergyInfo);
		/*One Socket*/
		if(socketNum == 1) {
			
			String[] energy = EnergyInfo.split("#");
			double[] stats = new double[energy.length];
			for(int j=0; j<energy.length; j++){
				stats[j] = Double.parseDouble(energy[j]);
			}
			return stats;

		} else {
		/*Multiple sockets*/
			String[] perSockEner = EnergyInfo.split("@");
			double[] stats = new double[3*socketNum];
			int count = 0;


			for(int i = 0; i < perSockEner.length; i++) {
				String[] energy = perSockEner[i].split("#");
				for(int j = 0; j < energy.length; j++) {
					count = i * 3 + j;	//accumulative count
					stats[count] = Double.parseDouble(energy[j]);
				}
			}
			return stats;
		}

	}

	public static void main(String[] args) {

		double[] before = getEnergyStats();
		try {
			Thread.sleep(1000);
		} catch(Exception e) {
		}
		double[] after = getEnergyStats();
		int corenum = after.length-2;
		System.out.println("Power consumption of dram: " + (after[0] - before[0]));
		for(int i = 1; i < corenum+1; i++) {
			System.out.println("power consumption of cpu: " + i + ": " + (after[1] - before[1]));
		}
		System.out.println("Power consumption of pakcage: " + (after[corenum+1] - before[corenum+1]));
		ProfileDealloc();
	}
}
