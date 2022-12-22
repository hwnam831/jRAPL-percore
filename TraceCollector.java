class Trace{

    public int socketnum;
    public int core_count;
    public String[] names;
    public long[] cycles;
    public long[] inst;
    public long[] aperf;
	public long[] mperf;

    //Instantenous readings. Need averaging
    public double[] voltages;
    public double[] temperatures;
    public int instantCount;

    //Perf counters
    public long[][] counters; 
    public double pkgE;
    public double dramE;
    public long time;
    public float maxfreq;

    public Trace(int core_count, String ctr_string, int socketnum){
        this.socketnum = socketnum;
        this.core_count = core_count;
        this.cycles = new long[core_count];
        this.inst = new long[core_count];
        this.aperf = new long[core_count];
        
        this.mperf = new long[core_count];
        this.voltages = new double[core_count];
        this.temperatures = new double[core_count];
        this.names = ctr_string.split(",");
        this.counters = new long[core_count][this.names.length];
        this.maxfreq = (float)EnergyCheckUtils.GetMaxFreq(socketnum*core_count);
        readCounters();
    }

    public void readCounters(){
        this.time = java.lang.System.currentTimeMillis();
        int offset = this.socketnum * this.core_count;
        pkgE = EnergyCheckUtils.GetPkgEnergy(socketnum);
		dramE = EnergyCheckUtils.GetDramEnergy(socketnum);
        for (int core=0; core<core_count; core++){
            aperf[core] = EnergyCheckUtils.GetAPERF(core+offset);
            mperf[core] = EnergyCheckUtils.GetMPERF(core+offset);
            if (instantCount == 0){
                voltages[core] = EnergyCheckUtils.GetCoreVoltage(core+offset);
                temperatures[core] = EnergyCheckUtils.getCoreTemp(core+offset);
            } else {
                voltages[core] += EnergyCheckUtils.GetCoreVoltage(core+offset);
                temperatures[core] += EnergyCheckUtils.getCoreTemp(core+offset);
                //instantCount++;
                voltages[core] = voltages[core] / (instantCount+1);
                temperatures[core] = temperatures[core] / (instantCount+1);
            }
            counters[core] = PerfCheckUtils.getMultiPerfCounter(core+offset);
            cycles[core] = EnergyCheckUtils.getClkCounter(core+offset);
            inst[core] = EnergyCheckUtils.getInstCounter(core+offset);
        }
        instantCount = 0;
    }
    public void instantRead(){
        int offset = this.socketnum * this.core_count;
        if(instantCount == 0){
            for (int core=0; core<core_count; core++){
                voltages[core] = EnergyCheckUtils.GetCoreVoltage(core+offset);
                temperatures[core] = EnergyCheckUtils.getCoreTemp(core+offset);
            }
        } else {
            for (int core=0; core<core_count; core++){
                voltages[core] += EnergyCheckUtils.GetCoreVoltage(core+offset);
                temperatures[core] += EnergyCheckUtils.getCoreTemp(core+offset);
            }
        }
        instantCount++;
    }

    public void copyFrom(Trace from){
        for (int core=0; core<core_count; core++){
            this.cycles[core] = from.cycles[core];
            this.inst[core] = from.inst[core];
            this.aperf[core] = from.aperf[core];
            this.mperf[core] = from.mperf[core];
            for(int i=0; i<this.names.length; i++)
                this.counters[core][i] = from.counters[core][i];
        }
        this.pkgE = from.pkgE;
        this.dramE = from.dramE;
        this.time = from.time;
    }
    public String headerCSV(){
        int offset = socketnum*core_count;
        String firstLine = ",DRAM Power(W):" + socketnum+",Package Power(W):" + socketnum;
        for (int i=offset; i<offset+core_count; i++){
            String linenow = ",Voltage:"+i+",Freq(kHz):"+i;
            linenow = linenow + ",Temp(C):" + i;
            linenow = linenow + ",instructions:" + i;
            linenow = linenow + ",cycle-count:" + i;
            for (String ctr:names){
                linenow = linenow + ","+ctr+":"+i;
            }
            
            firstLine += linenow;
        }
        return firstLine;
    }
    public String CSVString(Trace before){
        long duration = time - before.time;
        double truescale = 1000.0/duration;
        String curline = "," +(dramE - before.dramE)*truescale+"," +(pkgE - before.pkgE)*truescale;

        for (int core=0; core<core_count; core++){

            long daperf = aperf[core] - before.aperf[core];
            long dmperf = mperf[core] - before.mperf[core];
            curline += ","+voltages[core]+","+(int)((maxfreq*daperf)/dmperf);
            curline += "," + temperatures[core];
            curline += "," + (inst[core] - before.inst[core]);
            curline += "," + (cycles[core] - before.cycles[core]);
            
            for (int i=0; i<names.length; i++){
                curline += "," + (counters[core][i] - before.counters[core][i]);
            }
        }
        return curline;
    }
}

public class TraceCollector{

    public static void main(String[] args) {

        int durationms=60000;
        int sampleperiod = 20;
        
        //int socketNum = EnergyCheckUtils.GetSocketNum();
        
        

        //String counters = "cache-misses,instructions,cycle_activity.cycles_mem_any,branch-misses,uops_executed.core";
        String counters = "cycle_activity.stalls_ldm_pending,cache-misses,branch-misses,uops_executed.core";
        String[] ctrs = counters.split(",");
		String firstLine = "Time(ms),Duration(ms)";
        

		PerfCheckUtils.perfEventInit(counters, true);
        int threadspercore = PerfCheckUtils.getThreadPerCore();
        int threadNum = PerfCheckUtils.getCoreNum(); //number of logical cores
        int num_sockets = EnergyCheckUtils.GetSocketNum();
        Trace[] before = new Trace[num_sockets];
        Trace[] after = new Trace[num_sockets];
        for (int i=0; i<num_sockets; i++){
            after[i] = new Trace(threadNum/num_sockets, counters, i);
            firstLine += after[i].headerCSV();
            before[i] = new Trace(threadNum/num_sockets, counters, i);
        }
        System.out.println(firstLine);
		if (args.length >= 1){
			durationms = Integer.parseInt(args[0])*1000;
		} else {
            System.out.println("Usage: java Tracecollector [duration]");
            return;
        }



        
        
		for (int epc = 0; epc < durationms/sampleperiod; epc++){
            for (int i=0; i<num_sockets; i++){
                before[i].copyFrom(after[i]);
            }
            long curtimems=java.lang.System.currentTimeMillis();
            long elapse = curtimems - before[0].time;
            long nextPeriod = before[0].time + sampleperiod;
            if (elapse < 0){
                elapse = sampleperiod-1;
            }
            while(java.lang.System.currentTimeMillis() < nextPeriod)
                try {
                    Thread.sleep(2);
                    for (int i=0; i<num_sockets; i++){
                        after[i].instantRead(); //Voltages, Temperatures
                    }
                } catch(Exception e) {
                }
            for (int i=0; i<num_sockets; i++){
                after[i].readCounters();
            }

            String curline = ""+after[0].time + ","+ (after[0].time-before[0].time);

            for (int i=0; i<num_sockets; i++){
                curline += after[i].CSVString(before[i]);
            }
			System.out.println(curline);

		}

        EnergyCheckUtils.ProfileDealloc();
        PerfCheckUtils.perfDisable();
    }
}