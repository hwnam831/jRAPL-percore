import java.lang.reflect.Field;
public class PerfCheckUtils {
	
	public native static void perfInit(int numEvents, int isSet);
	public native static void singlePerfEventCheck(String eventNames);
	public native static void groupPerfEventsCheck(String eventNames);
	public native static void perfEnable();
	public native static void perfDisable();
	public native static void perfSingleRead(int id, long[] buffer);
	public native static void perfMultRead(long[] buffer);
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
	public static long[] getMultiPerfCounter() {
		long[] buffer = new long[3 + 2 * eventNum];
		long[] results = new long[eventNum];
		
		if(eventNum > 0) {
			perfMultRead(buffer);
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
		long[] preamble; 
		long[] epilogue;
		String counters = "cache-misses,cache-references,uops_executed.core,instructions,cycle_activity.cycles_mem_any,branch-instructions,branch-misses,uops_issued.stall_cycles,cpu-cycles";
		
//		perfEventInit(counters, false);
		perfEventInit(counters, true);

//		preamble = getSinglePerfCounter();
		preamble = getMultiPerfCounter();
		System.out.println("Testing...");

		for(int i = 0; i < test.length; i++) {
			test[i] *= test[i] + i;
		}
		System.out.println("Finish");
//		try {
//			Thread.sleep(1000);
//		} catch(Exception e) {
//		}
//		epilogue = getSinglePerfCounter();
		epilogue = getMultiPerfCounter();
		String[] ctrs = counters.split(",");
		for (int i=0; i<ctrs.length; i++){
			System.out.println(ctrs[i] + " is: " + (epilogue[i] - preamble[i]));
		}
		
		//System.out.println("cache references is: " + (epilogue[1] - preamble[1]));

		perfDisable();
	}
}

