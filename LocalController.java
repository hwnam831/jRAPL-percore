
import java.util.concurrent.locks.ReentrantLock;  
import java.io.*;  
import java.net.*; 
import java.util.*;
import java.util.ArrayDeque;
import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;


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
    boolean verbose = false;
    String csvfile;
    public TraceCollectorThread(int traceWindow, int periodms, String csvfile){
        this.traceWindow = traceWindow;
        this.periodms = periodms;
        this.lock = new ReentrantLock();
        this.csvfile = csvfile;
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
        PrintWriter fwriter;
        try {

            fwriter = new PrintWriter(new FileOutputStream(csvfile));
            String counters = "cycle_activity.stalls_ldm_pending,cache-misses,branch-misses,uops_executed.core";
            String[] ctrs = counters.split(",");
		    String firstLine = "Time(ms),Duration(ms)";
            for (int i=0; i<num_sockets; i++){
                firstLine += after[i].headerCSV();
            }
            fwriter.println(firstLine + ",Valid");
        } catch (Exception e) {
            fwriter = new PrintWriter(OutputStream.nullOutputStream());
        }
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
            if (ctr.valid || true) {
                String curline = ""+after[0].time + ","+ (after[0].time-before[0].time);

                for (int i=0; i<num_sockets; i++){
                    curline += after[i].CSVString(before[i]);
                }
                fwriter.println(curline + "," + ctr.valid);
            }
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
        fwriter.close();
    }
    
}  
 
class PowerControllerThread extends Thread{
    double[] pl1;
    double[] pl2;
    double[] curpl;
    int num_sockets;
    boolean running = true;
    String policy;
    public static final int port = 1234;
    public PowerControllerThread(double powerlimit){

        num_sockets = EnergyCheckUtils.GetSocketNum();
        pl1 = new double[num_sockets];
        pl2 = new double[num_sockets];
        curpl = new double[num_sockets];
        for (int s = 0; s<num_sockets; s++){
            double[] limitinfo = EnergyCheckUtils.GetPkgLimit(s);
            pl1[s] = limitinfo[0];
            pl2[s] = limitinfo[2];
            curpl[s] = powerlimit / num_sockets;
		    System.err.println("Power limit1 of pkg " + s + ": " + limitinfo[0] + "\t timewindow1 :" + limitinfo[1]);
		    System.err.println("Power limit2 of pkg " + s + ": " + limitinfo[2] + "\t timewindow2 :" + limitinfo[3]);
            System.err.println("Trying to set short term limit to " + curpl[s] + "W");
			EnergyCheckUtils.SetPkgLimit(s, curpl[s], curpl[s]);
        }
        


    }
    public void terminate(){
        running = false;
    }
    public void run(){
        try{
            ServerSocket ss = new ServerSocket(port);
            ss.setSoTimeout(2000); // try per 2 seconds
            while(running){
                try{
                    Socket s = ss.accept();
                    DataInputStream din=new DataInputStream(s.getInputStream());  
                    DataOutputStream dout=new DataOutputStream(s.getOutputStream());  
                    
                    String str="",str2="";
                    str=din.readUTF();
                    //System.out.println("client says: "+str);
                    String [] pls = str.split(",", curpl.length);
                    
                    for (int i=0; i<curpl.length; i++){
                        double pl = Double.parseDouble(pls[i]);
                        curpl[i] = pl;
                        EnergyCheckUtils.SetPkgLimit(i, curpl[i], curpl[i]);
                    }
                    din.close();
                    dout.close();
                    s.close();
                } catch (Exception e){
                    System.out.println("Timeout. Retrying:");
                }

            
            }
            ss.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int s = 0; s<num_sockets; s++){
            
            System.err.println("Reverting back to original limit");
			EnergyCheckUtils.SetPkgLimit(s, pl1[s], pl2[s]);
        }
    }
    
}  
public class LocalController{
    public static void main(String[] args){

        ArgumentParser parser = ArgumentParsers.newFor("LocalController").build()
                .defaultHelp(true);
        parser.addArgument("-p","--policy")
                .choices("fair", "slurm", "ml").setDefault("fair");
        parser.addArgument("-c", "--cap").type(Integer.class)
                .setDefault(100).help("Power cap for this node");
        parser.addArgument("--period").type(Integer.class)
                .setDefault(300).help("Control period in ms");
        parser.addArgument("--duration").type(Integer.class)
                .setDefault(60).help("Time duration in seconds");
        Namespace res;
        try{
            res = parser.parseArgs(args);
        } catch (ArgumentParserException e){
            parser.handleError(e);
            return;
        }


        
        PowerControllerThread pt = new PowerControllerThread((Integer)res.get("cap"));
        double[] curpl = pt.curpl.clone();
        double[] powerusage = new double[curpl.length];
        int timeperiodms = (Integer)res.get("period");
        int epochs = (((Integer) res.get("duration")) * 1000) / timeperiodms;
        String policy = (String) res.get("policy");
        TraceCollectorThread t = new TraceCollectorThread(16, 10, policy 
            + "_" + (Integer)res.get("cap") + "W.csv");
        t.start();
        pt.start();
        try{
        Thread.sleep(timeperiodms);
        } catch (Exception e){
            
        }
        for (int epc = 0; epc < epochs; epc++){
            try{
                Thread.sleep(timeperiodms);
            } catch (Exception e){
                    
            }
            if (t.perfCounters.size() < 4){
                continue;
            }
            t.lock.lock();
            String l = "";
            PerfCounters fctr = t.perfCounters.peekFirst();
            for (int i = 0; i<powerusage.length; i++){
                
                powerusage[i] = fctr.pkgCtrs[i][1];
            }

            for (PerfCounters ctr: t.perfCounters){
                for (int i = 0; i<powerusage.length; i++){
                    powerusage[i] = 0.75*powerusage[i] + 0.25*ctr.pkgCtrs[i][1];
                }
            }
            t.lock.unlock();
            double[] newpl = curpl.clone();
            double pool = 0.0;
            double alpha = curpl.length / (curpl.length - 0.99); //avoid divide-by-zero
            if (policy.equals("fair")){
                continue;
            }
            for (int i = 0; i<curpl.length; i++){
                double diff = curpl[i] - powerusage[i];
                
                if (diff > 0){
                    pool += diff * 0.5 * alpha;
                    newpl[i] -= diff*0.5*alpha;
                }
                
            }
            for (int i = 0; i<newpl.length; i++){
                newpl[i] += pool / newpl.length;
            }
            System.out.println("Cur power limit: " + Arrays.toString(curpl) + "\tCur power usage: " + 
            Arrays.toString(powerusage) + "\t New power limit: " + Arrays.toString(newpl));
            try{
                Socket s = new Socket("localhost", PowerControllerThread.port);
                DataInputStream din=new DataInputStream(s.getInputStream());  
                DataOutputStream dout=new DataOutputStream(s.getOutputStream());  
                String message = Arrays.toString(newpl);
                dout.writeUTF(message.substring(1,message.length()-1));
                din.close();
                dout.close();
                s.close();
                curpl = newpl;
            } catch (Exception e) {
                e.printStackTrace();
            }

            //System.out.println(l);
        }
        try{
        t.terminate();
        pt.terminate();
        t.join();
        pt.join();
        } catch (Exception e){

        }
        EnergyCheckUtils.ProfileDealloc();
        PerfCheckUtils.perfDisable();
    }
}