import java.lang.reflect.Field;
public class PerfCheckUtils {
	
	public native static void perfInit(int numEvents, int isSet);
	public native static void singlePerfEventCheck(String eventNames);
	public native static void groupPerfEventsCheck(String eventNames);
	public native static void perfEnable();
	public native static void perfDisable();
	public native static int getCoreNum();
	public native static void perfSingleRead(int id, long[] buffer);
	public native static void perfMultRead(int core_id, long[] buffer);
	public native static long processSingleValue(long[] buffer);
	public native static long[] processMultiValue(long[] buffer);
	
	public static int eventNum = 0;
	//For testing, Make the variable not be optimized as static
	static int[] test = new int[100000000];

	static {
		/* 
		System.setProperty("java.library.path", System.getProperty("user.dir"));
		try {
			Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
			fieldSysPath.setAccessible(true);
			fieldSysPath.set(null, null);
		} catch (Exception e) { }
		*/
		System.loadLibrary("perfCheck");
	}
	
	/**
	 * Initialize perf check utilities
	 * 
	 * @eventNames String names of hardware counters to be checked
	 * @isGrouped Do you want get @eventNames counters as single read? 
	 */
	public static void perfEventInit(String eventNames, boolean isGrouped) {
		int setGroup;
		String[] eventName = eventNames.split(",");
		eventNum = eventName.length;
		
		setGroup = isGrouped ? 1 : 0;
		perfInit(eventNum, setGroup);
		
		if(isGrouped) {
			groupPerfEventsCheck(eventNames);
		} else {
			singlePerfEventCheck(eventNames);
		}
		
		perfEnable();
	}
	
	/**
	 * Get multiple perf counter values with single read
	 */
	public static long[] getMultiPerfCounter(int core_id) {
		long[] buffer = new long[3 + 2 * eventNum];
		long[] results = new long[eventNum];
		
		if(eventNum > 0) {
			perfMultRead(core_id, buffer);
			results = processMultiValue(buffer);
		} else {
			System.err.println("event number is 0, should call perfEventInit first!");
			System.exit(-1);
		}
		
		return results;
	}
	
	/**
	 * Get one perf counter value a time
	 */
	public static long[] getSinglePerfCounter() {
		long[] buffer = new long[3];
		long[] results = new long[eventNum];
		
		if(eventNum > 0) {
			for(int i = 0; i < results.length; i++) {
				perfSingleRead(i, buffer);
				results[i] = processSingleValue(buffer);
			}
		} else {
			System.err.println("event number is 0, should call perfEventInit first!");
			System.exit(-1);
		}
		return results;
	}
	
	public static void main(String[] args) {
		
		
		//String counters = "cache-misses,cache-references,uops_executed.core,instructions,cycle_activity.cycles_mem_any,branch-instructions,branch-misses,uops_issued.stall_cycles,cpu-cycles";
		String counters = "cache-misses,instructions,uops_issued.stall_cycles,branch-misses,uops_executed.core";
		String[] ctrs = counters.split(",");
		
//		perfEventInit(counters, false);
		perfEventInit(counters, true);

		int coreNum = getCoreNum();
		System.out.println("Core count: " + coreNum);
		long[][] preamble = new long[coreNum][]; 
		long[][] epilogue = new long[coreNum][];
		
//		preamble = getSinglePerfCounter();

		for (int core=0; core<coreNum; core++){
			preamble[core] = getMultiPerfCounter(core);
		}
		

		for(int i = 0; i < test.length; i++) {
			test[i] *= test[i] + i;
		}
		System.out.println("Finish");
		System.out.print("CoreNum\t");
		System.out.println(String.join("\t",ctrs));
		try {
			Thread.sleep(10);
		} catch(Exception e) {
		}
//		epilogue = getSinglePerfCounter();
		long curtimems = java.lang.System.currentTimeMillis();
		for (int core=0; core<coreNum; core++){
			epilogue[core] = getMultiPerfCounter(core);
		}

		System.out.println("Time to read all: " + (java.lang.System.currentTimeMillis() - curtimems));
		for (int core=0; core<coreNum; core++){
			System.out.print(core);
			for (int i=0; i<ctrs.length; i++){
				System.out.print("\t" + (epilogue[core][i] - preamble[core][i]));
			}
			System.out.println();
		}
		perfDisable();
	}
}

