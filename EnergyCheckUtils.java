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
	public native static double GetPkgEnergy(int socketid);
	public native static double GetCoreVoltage(int coreid);
	// If disabled, powerlimit is set to -1
	// limit1: TDP (don't change) limit2: short-term (7.8ms)
	// 0: powerlimit1, 1: timewindow1, 2: powerlimit2, 3: timewindow2
	public native static double[] GetPkgLimit(int socketid);
	// Set the short-term limit (powerlimit2)
	public native static void SetPkgLimit(int socketid, double limit1, double limit2);
	public native static double GetDramEnergy(int socketid);
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

		int sampleperiod = 100;
		//3min by default
		int epochs  = 10*(1000/sampleperiod);
		double pl2 = -1;
		if (args.length >= 1){
			sampleperiod = Integer.parseInt(args[0]);
		}
		if (args.length >= 2){
			int duration = Integer.parseInt(args[1]);
			epochs  = duration*(1000/sampleperiod);
		}
		if (args.length >= 3){
			pl2 = Double.parseDouble(args[2]);
		}

		double scale = 1000.0/(double)sampleperiod;
		double pkgbefore, drambefore;
		double pkgafter, dramafter;
		pkgbefore = GetPkgEnergy(0);
		drambefore = GetDramEnergy(0);
		double[] limitinfo = GetPkgLimit(0);
		long curtimems;
		System.err.println("Power limit1 of pkg: " + limitinfo[0] + "\t timewindow1 :" + limitinfo[1]);
		System.err.println("Power limit2 of pkg: " + limitinfo[2] + "\t timewindow2 :" + limitinfo[3]);

		if (pl2 > 0){
			System.err.println("Trying to set short term limit to " + pl2 + "W");
			SetPkgLimit(0, pl2, pl2);
			double[] limitinfo2 = GetPkgLimit(0);
			System.err.println("Power limit1 of pkg: " + limitinfo2[0] + "\t timewindow1 :" + limitinfo2[1]);
			System.err.println("Power limit2 of pkg: " + limitinfo2[2] + "\t timewindow2 :" + limitinfo2[3]);
		}
		curtimems=java.lang.System.currentTimeMillis();
		System.out.println("Time(ms),DRAM Power(W),Package Power(W),corev");
		for (int epc = 0; epc < epochs; epc++){
			try {
				Thread.sleep(sampleperiod);
			} catch(Exception e) {
			}
			long newtimems = java.lang.System.currentTimeMillis();
			double truescale = 1000.0/(double)(newtimems-curtimems);
			pkgafter = GetPkgEnergy(0);
			dramafter = GetDramEnergy(0);
			curtimems = newtimems;
			System.out.print(""+curtimems + "," + (dramafter - drambefore)*truescale + "," +(pkgafter - pkgbefore)*truescale);
			System.out.println(","+GetCoreVoltage(0));
			pkgbefore = pkgafter;
			drambefore = dramafter;
		}
		if (pl2 > 0){
			System.err.println("Reverting back...");
			SetPkgLimit(0, limitinfo[0], limitinfo[2]);
			double[] limitinfo2 = GetPkgLimit(0);
			System.err.println("Power limit1 of pkg: " + limitinfo2[0] + "\t timewindow1 :" + limitinfo2[1]);
			System.err.println("Power limit2 of pkg: " + limitinfo2[2] + "\t timewindow2 :" + limitinfo2[3]);
		}
		ProfileDealloc();
	}
}
