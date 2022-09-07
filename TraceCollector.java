public class TraceCollector{

    public static void main(String[] args) {

        int durationms=60000;
        int sampleperiod = 20;
        int threadspercore = 2;
        //int socketNum = EnergyCheckUtils.GetSocketNum();
        
        

        String counters = "cache-misses,instructions,uops_issued.stall_cycles,branch-misses,uops_executed.core";
        String[] ctrs = counters.split(",");
		String firstLine = "Time(ms),Duration(ms),DRAM Power(W),Package Power(W)";
        

		PerfCheckUtils.perfEventInit(counters, true);
        int threadNum = PerfCheckUtils.getCoreNum(); //number of logical cores
        //System.out.print("CoreNum\t" + threadNum + "\n");
        for (int i=0; i<threadNum; i++){
            String linenow = ",Voltage:"+i+",Freq(kHz):"+i;
            for (String ctr:ctrs){
                linenow = linenow + ","+ctr+":"+i;
            }
            firstLine += linenow;
        }
        System.out.println(firstLine);
		if (args.length >= 1){
			durationms = Integer.parseInt(args[0])*1000;
		} else {
            System.out.println("Usage: java Tracecollector [duration]");
            return;
        }


		float maxfreq = (float)EnergyCheckUtils.GetMaxFreq(0);

        long[] aperf = new long[threadNum/threadspercore];
        
		long[] mperf = new long[threadNum/threadspercore];
        long[][] preamble = new long[threadNum][]; 
		long[][] epilogue = new long[threadNum][];

        long curtimems=java.lang.System.currentTimeMillis();
        for (int core=0; core<threadNum/threadspercore; core++){
            aperf[core] = EnergyCheckUtils.GetAPERF(core);
            mperf[core] = EnergyCheckUtils.GetMPERF(core);
        }
        
        double pkgbefore, drambefore;
		double pkgafter, dramafter;
        
		pkgbefore = EnergyCheckUtils.GetPkgEnergy(0);
		drambefore = EnergyCheckUtils.GetDramEnergy(0);


		for (int thread=0; thread<threadNum; thread++){
			preamble[thread] = PerfCheckUtils.getMultiPerfCounter(thread);
		}
        
		for (int epc = 0; epc < durationms/sampleperiod; epc++){

			try {
				Thread.sleep(sampleperiod);
			} catch(Exception e) {
			}

            long duration = java.lang.System.currentTimeMillis() - curtimems;
			double truescale = 1000.0/duration;
			pkgafter = EnergyCheckUtils.GetPkgEnergy(0);
			dramafter = EnergyCheckUtils.GetDramEnergy(0);

			
			System.out.print(""+curtimems + ","+ duration + "," + (dramafter - drambefore)*truescale + "," +(pkgafter - pkgbefore)*truescale);
            for (int thread=0; thread<threadNum; thread++){
                epilogue[thread] = PerfCheckUtils.getMultiPerfCounter(thread);
                int core = thread/threadspercore;
                long daperf = EnergyCheckUtils.GetAPERF(core) - aperf[core];
			    long dmperf = EnergyCheckUtils.GetMPERF(core) - mperf[core];
                System.out.print(","+EnergyCheckUtils.GetCoreVoltage(core)+","+(int)((maxfreq*daperf)/dmperf));
                for (int i=0; i<ctrs.length; i++){
                    System.out.print("," + (epilogue[thread][i] - preamble[thread][i]));
                    preamble[thread][i] = epilogue[thread][i];
                }
                
                aperf[core] = EnergyCheckUtils.GetAPERF(core);
                mperf[core] = EnergyCheckUtils.GetMPERF(core);
            }
			System.out.println();
			
            curtimems = java.lang.System.currentTimeMillis();
			pkgbefore = pkgafter;
			drambefore = dramafter;
		}

        EnergyCheckUtils.ProfileDealloc();
        PerfCheckUtils.perfDisable();
    }
}