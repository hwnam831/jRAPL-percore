
import java.util.concurrent.locks.ReentrantLock;  

import java.util.ArrayDeque;

class PerfCounters{
    public long timems;
    public float[][] pkgCtrs;
    public static final int freqRange = 4000000;
    public static final int tempRange = 90;
    public static final float perNsMax = 40;
    public float[][] coreCtrs;
    public static boolean validate(boolean valid, float min, float max, float val){
        return valid && val > min && val < max;
    }
    public boolean valid = true;
    public PerfCounters(Trace[] before, Trace after[]){
        //Drampower, pkgpower
        pkgCtrs = new float[after.length][2];
        //voltage,freq,temp,inst,cycle and counters
        int cps = after[0].core_count;
        coreCtrs = new float[cps * after.length][5+after[0].names.length];
        timems = after[0].time;
        for (int socket = 0; socket < after.length; socket++){
            long duration = after[socket].time - before[socket].time;
            float duration_ns = (float)(duration * 1e6);
            float truescale = (float)(1000.0/duration);
            pkgCtrs[socket][0] =(float)(after[socket].dramE - before[socket].dramE)*truescale;
            pkgCtrs[socket][1] = (float)(after[socket].pkgE - before[socket].pkgE)*truescale;
            valid = validate(valid, 0, 300, pkgCtrs[socket][0]);
            valid = validate(valid, 0, 300, pkgCtrs[socket][1]);
            for (int core=0; core<cps; core++){
                long daperf = after[socket].aperf[core] - before[socket].aperf[core];
                long dmperf = after[socket].mperf[core] - before[socket].mperf[core];
                coreCtrs[core+socket*cps][0] = (float)after[socket].voltages[core];
                coreCtrs[core+socket*cps][1] = (float)((after[0].maxfreq*daperf)/dmperf/freqRange);
                coreCtrs[core+socket*cps][2] = (float)(after[socket].temperatures[core]/tempRange);

                float bips = (float)(after[socket].inst[core] - before[socket].inst[core])/duration_ns;
                valid = validate(valid, 0, perNsMax, bips);
                coreCtrs[core+socket*cps][3] = bips;

                float bcps = (float)(after[socket].cycles[core] - before[socket].cycles[core])/duration_ns;
                valid = validate(valid, 0, 4, bcps);
                coreCtrs[core+socket*cps][4] = bcps;

                for (int i=0; i<after[0].names.length; i++){
                    float bps = (float)(after[socket].counters[core][i] - 
                        before[socket].counters[core][i])/duration_ns;
                    valid = validate(valid, 0, perNsMax, bps);
                    coreCtrs[core+socket*cps][5+i] = bps;
                }
            }
        }
    }
    public PerfCounters(PerfCounters original){
        //Drampower, pkgpower
        pkgCtrs = new float[original.pkgCtrs.length][];
        //voltage,freq,temp,inst,cycle and counters
        timems = original.timems;
        coreCtrs = new float[original.coreCtrs.length][];

        for (int socket = 0; socket < original.pkgCtrs.length; socket++){
            pkgCtrs[socket] = original.pkgCtrs[socket].clone();
        }
        for (int core=0; core<original.coreCtrs.length; core++){
            coreCtrs[core] = original.coreCtrs[core].clone();
        }
    }
}

class TraceCollectorThread extends Thread{
    Trace[] before;
    Trace[] after;
    int num_sockets;
    int threadNum;
    int periodms;
    ReentrantLock lock;
    public ArrayDeque<PerfCounters> perfCounters;
    public int traceWindow;
    private boolean running = true;
    public TraceCollectorThread(int traceWindow, int periodms){
        this.traceWindow = traceWindow;
        this.periodms = periodms;
        this.lock = new ReentrantLock();
        String counters = "cycle_activity.stalls_ldm_pending,cache-misses,branch-misses,uops_executed.core";
        String[] ctrs = counters.split(",");
		String firstLine = "Time(ms),Duration(ms)";
        

		PerfCheckUtils.perfEventInit(counters, true);
        
        threadNum = PerfCheckUtils.getCoreNum(); //number of logical cores
        num_sockets = EnergyCheckUtils.GetSocketNum();
        before = new Trace[num_sockets];
        after = new Trace[num_sockets];
        for (int i=0; i<num_sockets; i++){
            after[i] = new Trace(threadNum/num_sockets, counters, i);
            firstLine += after[i].headerCSV();
            before[i] = new Trace(threadNum/num_sockets, counters, i);
        }
        perfCounters = new ArrayDeque<PerfCounters>();


    }
    public void terminate(){
        running = false;
    }
    public void run(){
        while (running){
            for (int i=0; i<num_sockets; i++){
                before[i].copyFrom(after[i]);
            }
            long curtimems=java.lang.System.currentTimeMillis();
            long elapse = curtimems - before[0].time;
            long nextPeriod = before[0].time + periodms;
            if (elapse < 0){
                elapse = periodms-1;
            }
            while(java.lang.System.currentTimeMillis() < nextPeriod)
                try {
                    Thread.sleep(1);
                    for (int i=0; i<num_sockets; i++){
                        after[i].instantRead(); //Voltages, Temperatures
                    }
                } catch(Exception e) {
                }
            for (int i=0; i<num_sockets; i++){
                after[i].readCounters();
            }
            PerfCounters ctr = new PerfCounters(before, after);
            lock.lock();
            if (ctr.valid) {
                
                perfCounters.add(ctr);
                
            } else {
                if (!perfCounters.isEmpty()){
                    ctr = new PerfCounters(perfCounters.peekLast());
                    perfCounters.add(ctr);
                    
                }
            }
            if (perfCounters.size() > traceWindow){
                perfCounters.removeFirst();
            }
            lock.unlock();
        }
    }
    
}  

public class LocalController{
    public static void main(String[] args){
        TraceCollectorThread t = new TraceCollectorThread(16, 10);
        t.start();
        try{
        Thread.sleep(300);
        } catch (Exception e){
            
        }
        for (int epc = 0; epc < 60; epc++){
            try{
                Thread.sleep(300);
                } catch (Exception e){
                    
                }
            t.lock.lock();
            String l = "";
            for (PerfCounters ctr: t.perfCounters){
                for (float[] pctrs : ctr.pkgCtrs){
                    l = l + pctrs[0] + "," + pctrs[1] + "\n";
                }
            }
            t.lock.unlock();
            System.out.println(l);
        }
        try{
        t.terminate();
        t.join();
        } catch (Exception e){

        }
        EnergyCheckUtils.ProfileDealloc();
        PerfCheckUtils.perfDisable();
    }
}