
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
    public static final float perNsMax = 100;
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
    public final double default_pl1 = 105.0;
    public final double default_pl2 = 126.0;
    public final int default_timewindow = 1000;
    int num_sockets;
    boolean running = true;
    String policy;
    public static final int port = 1234;
    public PowerControllerThread(double powerlimit, int timeperiod){

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
            System.err.println("Trying to set running average timewindow to " + timeperiod + "ms");
            EnergyCheckUtils.SetRAPLTimeWindow(s, timeperiod);
            System.err.println("Trying to set running average limit to " + curpl[s] + "W");
			EnergyCheckUtils.SetPkgLimit(s, curpl[s]*1.1, curpl[s]*1.3);
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
			EnergyCheckUtils.SetPkgLimit(s, default_pl1, default_pl2);
            EnergyCheckUtils.SetRAPLTimeWindow(s, default_timewindow);
        }
    }
    
}  
public class LocalController{
    public static final double lr_max = 2.0;
    public static final double lr_min = -2.0;
    public static final float grad_max = 5.0f;
    public static final double power_min = 17;
    public static final double power_max = 105;
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
                .choices("fair", "slurm", "slurm2", "ml", "localml","localml2", "ml1", "ml2", "ml3").setDefault("fair");
        parser.addArgument("-c", "--cap").type(Integer.class)
                .setDefault(150).help("Power cap for this node");
        parser.addArgument("--period").type(Integer.class)
                .setDefault(100).help("Control period in ms");
        parser.addArgument("--lr").type(Double.class)
                .setDefault(1.0).help("Gradient-to-powercap rate");
        parser.addArgument("--sampleperiod").type(Integer.class)
                .setDefault(20).help("Sample period in ms");
        parser.addArgument("--duration").type(Integer.class)
                .setDefault(60).help("Time duration in seconds");
        parser.addArgument("--alpha").type(Double.class)
                .setDefault(0.25).help("Adjustment rate to make PL and power closer");
        parser.addArgument("--tag").type(String.class)
                .setDefault("").help("CSV filename tag");
        Namespace res;
        try{
            res = parser.parseArgs(args);
        } catch (ArgumentParserException e){
            parser.handleError(e);
            return;
        }
        

        double totalcap = (Integer)res.get("cap");
        double alpha = (Double)res.get("alpha");
        double lr = (Double)res.get("lr");
        int timeperiodms = (Integer)res.get("period");
        int sampleperiodms = (Integer)res.get("sampleperiod");
        PowerControllerThread pt = new PowerControllerThread((Integer)res.get("cap"),timeperiodms);
        double[] curpl = pt.curpl.clone();
        float[] powerusage = new float[curpl.length];
        double tolerance = 0.0;
        int epochs = (((Integer) res.get("duration")) * 1000) / timeperiodms;
        String policy = (String) res.get("policy");
        String tag = (String) res.get("tag");
        TraceCollectorThread t = new TraceCollectorThread(16, sampleperiodms, policy 
            +"_" + tag + "_" + (Integer)res.get("cap") + "W.csv", timeperiodms);
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
        float[][] core_bips = new float[num_pkg][core_per_pkg];
        float[] AvgBIPS = new float[num_pkg];
        float[] AvgPOW = new float[num_pkg];
        float[][] perfpredictions = new float[num_pkg][core_per_pkg];
        long curtimems = java.lang.System.currentTimeMillis();
        long basetime = curtimems;
        long nextPeriod = curtimems + timeperiodms;
        for (int epc = 0; epc < epochs; epc++){

            
            curtimems = java.lang.System.currentTimeMillis();
            try{
                Thread.sleep(nextPeriod - curtimems);

            } catch (Exception e){
                System.err.println("error in sleep");
            }
            nextPeriod = nextPeriod + timeperiodms;
            //System.err.println(nextPeriod - basetime);
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
                AvgPOW[i] = AvgPOW[i]*0.9f + powerusage[i]*0.1f;
                if (epc == 0){
                    AvgPOW[i] = powerusage[i];
                }
            }

            for (int i = 0; i<curperf.length; i++){
                
                curperf[i] = 0;

                for (int j=0; j<core_per_pkg; j++){
                    
                    core_bips[i][j] = t.moving_input[i*core_per_pkg*9 + j*9 + 3];
                    curperf[i] += core_bips[i][j];
                }
                AvgBIPS[i] = AvgBIPS[i]*0.9f + curperf[i]*0.1f;
                if (epc == 0){
                    AvgBIPS[i] = curperf[i];
                }

            }
            endmodel.inference(t.moving_input);
            float[][] freqs = new float[num_pkg][core_per_pkg];
            float[] avgfreqs = new float[num_pkg];
            for (int pkg=0; pkg<t.num_sockets; pkg++){
                avgfreqs[pkg] = 0;
                for (int core=0; core<core_per_pkg; core++){
                    freqs[pkg][core] = fctr.coreCtrs[core + pkg*core_per_pkg][1];
                    avgfreqs[pkg] += PerfCounters.freqRange*freqs[pkg][core]/core_per_pkg;

                }
            }
            t.lock.unlock();
            if(epc >= 1){
                endmodel.update_bias(powerusage, predictions);
                endmodel.update_perf_bias(core_bips, perfpredictions);
            }
            predictions = endmodel.predict_power(freqs);
            perfpredictions = endmodel.predict_perf(freqs);
            float[] edp_gradients = endmodel.getGlobalEDPGradients(freqs); // gradients per socket
            if (policy.equals("localml2")){
                //edp_gradients = endmodel.getLocalB2PGradients(freqs, curperf, powerusage);
                edp_gradients = endmodel.getLocalB2PGradients(freqs, AvgBIPS, AvgPOW);
            }

            System.out.print("Cur power usage," + Arrays.toString(powerusage).replace('[', ' ').replace(']',' ') + 
                ",Freq," + Arrays.toString(avgfreqs).replace('[', ' ').replace(']',' ') +
                ",Prediction," + Arrays.toString(predictions).replace('[', ' ').replace(']',' ') +
                ",Gradients," + Arrays.toString(edp_gradients).replace('[', ' ').replace(']',' '));
            float[] pkgbipspredictions = new float[num_pkg];
            for (int pkg=0; pkg<t.num_sockets; pkg++){
                pkgbipspredictions[pkg] = 0;
                for (int core=0; core<core_per_pkg; core++){
                    pkgbipspredictions[pkg] += perfpredictions[pkg][core];
                }
            }
            System.out.print(",Cur perf," + Arrays.toString(curperf).replace('[', ' ').replace(']',' ') + 
                ",Bips Prediction," + Arrays.toString(pkgbipspredictions).replace('[', ' ').replace(']',' '));
            double[] newpl = curpl.clone();
            double pool = 0.0;
            final double min_pool = 2.0;
            double beta = curpl.length / (curpl.length - 0.99); //avoid divide-by-zero
            if (policy.equals("slurm")){
                 double total_curpower = 0;
                for (int i = 0; i<curpl.length; i++){
                    double diff = curpl[i] - powerusage[i];
                    total_curpower += powerusage[i];
                    if (diff > 0){
                        pool += diff * 0.5 * beta;
                        newpl[i] -= diff*0.5*beta;
                        
                    }
                
                }
                //tolerance = (tolerance + totalcap - total_curpower)*0.5;
                for (int i = 0; i<newpl.length; i++){
                    newpl[i] += (tolerance + pool) / newpl.length;
                }
            } else if (policy.equals("slurm2")){
                double total_curpower = 0;
                for (int i = 0; i<curpl.length; i++){
                    double diff = curpl[i] - powerusage[i];
                    total_curpower += powerusage[i];
                    if (diff > 0){
                        pool += diff * 0.5 * beta;
                        newpl[i] -= diff*0.5*beta;
                        
                    }
                
                }
                tolerance = tolerance*0.9 + (totalcap - total_curpower)*0.1;
                System.out.print("Tolerance," + tolerance + ",");
                
                for (int i = 0; i<newpl.length; i++){
                    newpl[i] += (tolerance + pool) / newpl.length;
                }

            } else if (policy.equals("ml")){
                double sum_newpl = 0;
                double total_curpower = 0;
                double grad_sum = 0;
                for (int i = 0; i<newpl.length; i++){
                    newpl[i] = curpl[i] - alpha*(curpl[i] - powerusage[i]) + lr*edp_gradients[i];
                    sum_newpl += newpl[i];
                    total_curpower += powerusage[i];
                    grad_sum +=  edp_gradients[i];
                }
                tolerance = (tolerance + totalcap - total_curpower)*0.5;

                if (sum_newpl > totalcap + tolerance){
                    double delta = totalcap + tolerance - total_curpower;
                    double newlr = delta / grad_sum;
                    for (int i = 0; i<newpl.length; i++){
                        newpl[i] = powerusage[i] + newlr*edp_gradients[i];
                    }
                }

            } else if (policy.equals("localml")){
                for (int i = 0; i<newpl.length; i++){
                    newpl[i] = curpl[i] - alpha*(curpl[i] - powerusage[i]) + lr*edp_gradients[i];
                    newpl[i] = newpl[i] > totalcap/newpl.length ? totalcap/newpl.length : newpl[i];                
                }
            
            } else if (policy.equals("localml2")){
                
                for (int i = 0; i<newpl.length; i++){
                    newpl[i] = curpl[i] - alpha*(curpl[i] - powerusage[i]) + lr*edp_gradients[i];
                    newpl[i] = newpl[i] > totalcap/newpl.length ? totalcap/newpl.length : newpl[i];                
                }
            
            } else if (policy.equals("ml1")){
                // Reduce equally if exceeds
                double sum_newpl = 0;
                double grad_sum=0;
                double total_curpower = 0;
                for (int i = 0; i<newpl.length; i++){
                    grad_sum += edp_gradients[i];
                    total_curpower += powerusage[i];              
                }
                if (grad_sum > grad_max){
                    lr = grad_max/grad_sum;
                } else if(grad_sum < -grad_max){
                    lr = -grad_max/grad_sum;
                } else {
                    lr = 1;
                }
                tolerance = (tolerance + totalcap - total_curpower)*0.5;
                for (int i = 0; i<newpl.length; i++){
                    
                    newpl[i] = curpl[i] - alpha*(curpl[i] - powerusage[i]) + lr*edp_gradients[i];
                    if (newpl[i] > power_max){
                        newpl[i] = power_max;
                    } else if (newpl[i] < power_min){
                        newpl[i] = power_min;
                    }
                    sum_newpl += newpl[i];              
                }
                double remainder = 0;
                int eff_len = newpl.length;
                double[] coefs = new double[newpl.length];
                if (sum_newpl > totalcap + tolerance){
                    double delta = (sum_newpl - totalcap - tolerance)/newpl.length;
                    for (int i = 0; i<newpl.length; i++){
                        newpl[i] -= delta;
                        if (newpl[i] < power_min){
                            remainder += power_min - newpl[i];
                            eff_len--;
                            newpl[i] = power_min;
                            coefs[i] = 0;
                        }else{
                            coefs[i] = 1;
                        }
                    }
                    for (int i = 0; i<newpl.length; i++){
                        if(eff_len <= 0){
                            break;
                        }
                        newpl[i] -= coefs[i]*remainder/eff_len;
                    }
                }

            } else if (policy.equals("ml2")){
                // +-1 if exceeds
                
                double sum_newpl = 0;
                double total_curpower = 0;
                double grad_sum = 0;
                for (int i = 0; i<newpl.length; i++){
                    newpl[i] = curpl[i] - alpha*(curpl[i] - powerusage[i]) + lr*edp_gradients[i];
                    sum_newpl += newpl[i];
                    total_curpower += powerusage[i];
                    grad_sum +=  edp_gradients[i];
                }
                tolerance = (tolerance + totalcap - total_curpower)*0.5;
                tolerance = tolerance > 0 ? tolerance : 0;
                float[] bips_grads = endmodel.getBIPSGradients(freqs);
                float[] power_grads = endmodel.getPowerGradients(freqs);
                if (sum_newpl > totalcap + tolerance){
                    double delta = totalcap + tolerance - total_curpower;
                    float avg_grad = 0;
                    for (int i = 0; i<newpl.length; i++){
                        avg_grad += bips_grads[i]/power_grads[i]/newpl.length;
                    }
                    for (int i = 0; i<newpl.length; i++){
                        float adjust = bips_grads[i]/power_grads[i] > avg_grad ? 1 : -1;
                        newpl[i] = powerusage[i] + delta/newpl.length + adjust;
                    }
                }

            } else if (policy.equals("ml3")){
                // Reduce equally if exceeds
                double sum_newpl = 0;
                double total_curpower = 0;
                double grad_sum = 0;
                double epsilon = 2.0 * newpl.length;
                for (int i = 0; i<newpl.length; i++){
                    newpl[i] = curpl[i] - alpha*(curpl[i] - powerusage[i]) + lr*edp_gradients[i];
                    sum_newpl += newpl[i];
                    total_curpower += powerusage[i];
                    grad_sum +=  edp_gradients[i];
                }
                tolerance = (tolerance + totalcap - total_curpower)*0.5;
                if (sum_newpl > totalcap + tolerance){
                    double delta = totalcap + tolerance - total_curpower + epsilon;
                    double newlr = delta / grad_sum;
                    for (int i = 0; i<newpl.length; i++){
                        newpl[i] = powerusage[i] + newlr*edp_gradients[i] - epsilon/newpl.length;
                    }
                }

            } else {
                System.out.println(",Cur power limit," + arrToStr(curpl) +
                ",New power limit," + arrToStr(newpl) + ",Time," + (curtimems-basetime));
                continue; //Assume fair policy
            }
            
            System.out.println(",Cur power limit," + arrToStr(curpl) +
                ",New power limit," + arrToStr(newpl) + ",Time," + (curtimems-basetime));
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