
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
        return valid && val > min-0.01 && val < max;
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
                    if (!(bps > -0.01 && bps < perNsMax)){
                        System.err.println("Invalid " + after[0].names[i] + " val: " + bps + " max: " + perNsMax);
                    }
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
    int ctr_period;
    ReentrantLock lock;
    public ArrayDeque<PerfCounters> perfCounters;
    public float[] moving_input;
    public float[] moving_power;
    private int tracecount;
    public int traceWindow;
    private boolean running = true;
    boolean verbose = false;
    String csvfile;
    public TraceCollectorThread(int traceWindow, int periodms, String csvfile, int ctr_period){
        this.traceWindow = traceWindow;
        this.periodms = periodms;
        this.lock = new ReentrantLock();
        this.csvfile = csvfile;
        this.ctr_period = ctr_period;
        String counters = "cycle_activity.stalls_ldm_pending,cache-misses,branch-misses,uops_executed.core";
        
        

		PerfCheckUtils.perfEventInit(counters, true);
        
        threadNum = PerfCheckUtils.getCoreNum(); //number of logical cores
        //System.out.println("Threadnum " + threadNum);
        num_sockets = EnergyCheckUtils.GetSocketNum();
        before = new Trace[num_sockets];
        after = new Trace[num_sockets];
        for (int i=0; i<num_sockets; i++){
            after[i] = new Trace(threadNum/num_sockets, counters, i);

            before[i] = new Trace(threadNum/num_sockets, counters, i);
        }
        perfCounters = new ArrayDeque<PerfCounters>();
        this.tracecount = 0;
        moving_input = new float[threadNum*9];
        moving_power = new float[num_sockets];
        for (int i=0; i<moving_power.length; i++){
            moving_power[i] = 0;
        }
        for (int i=0; i<moving_input.length; i++){
            moving_input[i] = 0;
        }

    }
    public void terminate(){
        running = false;
    }
    //
    private void update_input(PerfCounters pctr){
        float alpha = tracecount < (float)ctr_period/periodms ? 
                (float)1/tracecount : (float)periodms/ctr_period;
        //float alpha = (float)periodms/(float)ctr_period;
        //System.out.println(alpha);
        if (tracecount == 0){
            return;
        }
        int cps = threadNum/num_sockets;
        for (int pkg=0; pkg<num_sockets; pkg++){
            int offset = pkg*cps*9;
            
            moving_power[pkg] = (1-alpha)*moving_power[pkg] +
                alpha*pctr.pkgCtrs[pkg][1];
            for (int core=0; core<cps; core++){
                for (int c=0; c<pctr.coreCtrs[0].length; c++){
                    moving_input[offset + core*9 + c] = (1-alpha)*moving_input[offset+ core*9 + c] + 
                        alpha*pctr.coreCtrs[pkg*cps+core][c];
                }
            }
        }

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
                    Thread.sleep(2);
                    for (int i=0; i<num_sockets; i++){
                        after[i].instantRead(); //Voltages, Temperatures
                    }
                } catch(Exception e) {
                }
            for (int i=0; i<num_sockets; i++){
                after[i].readCounters();
            }
            PerfCounters ctr = new PerfCounters(before, after);

            String curline = ""+after[0].time + ","+ (after[0].time-before[0].time);

            for (int i=0; i<num_sockets; i++){
                curline += after[i].CSVString(before[i]);
            }
            fwriter.println(curline + "," + ctr.valid);
            
            lock.lock();
            if (ctr.valid) {
                perfCounters.add(ctr);
                tracecount++;
                update_input(ctr);
                
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
                    System.err.println("Timeout. Retrying:");
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
    public static String arrToStr(float[] arr){
        return Arrays.toString(arr).replace('[', ' ').replace(']',' ');
    }
    public static String arrToStr(double[] arr){
        return Arrays.toString(arr).replace('[', ' ').replace(']',' ');
    }
    public static void main(String[] args){

        ArgumentParser parser = ArgumentParsers.newFor("LocalController").build()
                .defaultHelp(true);
        parser.addArgument("-p","--policy")
                .choices("fair", "slurm", "ml").setDefault("fair");
        parser.addArgument("-c", "--cap").type(Integer.class)
                .setDefault(100).help("Power cap for this node");
        parser.addArgument("--period").type(Integer.class)
                .setDefault(300).help("Control period in ms");
        parser.addArgument("--sampleperiod").type(Integer.class)
                .setDefault(20).help("Sample period in ms");
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
        float[] powerusage = new float[curpl.length];
        int timeperiodms = (Integer)res.get("period");
        int sampleperiodms = (Integer)res.get("sampleperiod");
        int epochs = (((Integer) res.get("duration")) * 1000) / timeperiodms;
        String policy = (String) res.get("policy");
        TraceCollectorThread t = new TraceCollectorThread(16, sampleperiodms, policy 
            + "_" + (Integer)res.get("cap") + "W.csv", timeperiodms);
        int num_pkg = t.num_sockets;
        int core_per_pkg = t.threadNum/t.num_sockets;
        MLModel endmodel = new MLModel(num_pkg, core_per_pkg , 6, "c220g2_power.pt", "c220g2_bips.pt");
        t.start();
        pt.start();
        try{
        Thread.sleep(timeperiodms);
        } catch (Exception e){
            
        }
        float[] predictions = new float[num_pkg];
        float[] curperf = new float[num_pkg];
        float[] perfpredictions = new float[num_pkg];
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
            /** 
            for (int i = 0; i<powerusage.length; i++){
                
                powerusage[i] = fctr.pkgCtrs[i][1];
            }

            for (PerfCounters ctr: t.perfCounters){
                for (int i = 0; i<powerusage.length; i++){
                    powerusage[i] = 0.75*powerusage[i] + 0.25*ctr.pkgCtrs[i][1];
                }
            }
            */
            for (int i = 0; i<powerusage.length; i++){
                
                powerusage[i] = t.moving_power[i];

            }
            for (int i = 0; i<curperf.length; i++){
                
                curperf[i] = 0;

                for (int j=0; j<fctr.coreCtrs[i].length; j++){
                    curperf[i] += fctr.coreCtrs[i][3];
                }

            }
            endmodel.inference(t.moving_input);
            float[][] freqs = new float[num_pkg][core_per_pkg];
            for (int pkg=0; pkg<t.num_sockets; pkg++){
                for (int core=0; core<core_per_pkg; core++){
                    freqs[pkg][core] = fctr.coreCtrs[core + pkg*core_per_pkg][1];

                }
            }
            t.lock.unlock();
            if(epc >= 1){
                endmodel.update_bias(powerusage, predictions);
            }
            predictions = endmodel.predict_power(freqs);
            perfpredictions = endmodel.predict_perf(freqs);
            float[] edp_gradients = endmodel.getPerfGradients(freqs); // gradients per socket
            System.out.print("Cur power usage," + Arrays.toString(powerusage).replace('[', ' ').replace(']',' ') + 
                ",Prediction," + Arrays.toString(predictions).replace('[', ' ').replace(']',' ') +
                ",Gradients," + Arrays.toString(edp_gradients).replace('[', ' ').replace(']',' '));
            System.out.print("Cur perf," + Arrays.toString(curperf).replace('[', ' ').replace(']',' ') + 
                ",Bips Prediction," + Arrays.toString(perfpredictions).replace('[', ' ').replace(']',' '));
            double[] newpl = curpl.clone();
            double pool = 0.0;
            final double min_pool = 2.0;
            double alpha = curpl.length / (curpl.length - 0.99); //avoid divide-by-zero
            if (policy.equals("slurm")){
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
            } else if (policy.equals("ml")){
                for (int i = 0; i<curpl.length; i++){
                    double diff = curpl[i] - powerusage[i];
                    
                    if (diff > 0){
                        pool += 0.5*diff;
                        newpl[i] -= 0.5*diff;
                    }
                
                }
                if (pool < min_pool) {
                    double extra_pool = min_pool - pool;
                    pool = min_pool;
                    for (int i=0; i<newpl.length; i++){
                        newpl[i] -= extra_pool/newpl.length;
                    }
                }
                
                float grad_sum = 0;
                for (float g: edp_gradients){
                    grad_sum += g;
                }
                for (int i=0; i<newpl.length; i++){
                    newpl[i] += pool*edp_gradients[i]/grad_sum;
                }

            } else {
                continue; //Assume fair policy
            }
            
            System.out.println(",Cur power limit," + arrToStr(curpl) +
                ",New power limit," + arrToStr(newpl));
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