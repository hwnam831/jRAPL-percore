
import java.util.concurrent.locks.ReentrantLock;  
import java.io.*;  
import java.net.*; 
import java.util.*;

import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

class CSVRecord {
    public String[] headers;

    double[] records;
    public CSVRecord(String[] headers, int pkgcount){
        this.headers = new String[2*headers.length + 1];
        this.headers[0] = "Time(ms)";
        for (int i=0; i<pkgcount; i++){
            for(int j=0; j<headers.length; j++){
                this.headers[i*headers.length+j+1] = headers[j] + ":" + i;
            }
        }
        System.err.println(this.headers);
    }
    public void newLine(){
        records = new double[headers.length];
        for (int i=0;i<records.length;i++){
            records[i]=0;
        }
    }
    private int indexOf(String header){
        for (int i=0 ;i<headers.length; i++){
            if (headers[i].equals(header))
                return i;
        }
        return -1;
    }
    public void addRecord(String header, double record){
        int idx = indexOf(header);
        if (idx >= 0){
            records[idx] = record;
        }
    }
    public void printHeader(PrintStream out){
        out.println(Arrays.toString(headers).replace('[', ' ').replace(']',' '));
    }
    public void printCSV(PrintStream out){
        out.println(Arrays.toString(records).replace('[', ' ').replace(']',' '));
    }
}

class HierarchicalAgent extends Thread{
    public static void main(String[] args){
        for (int i=0; i<100; i++){
            try{
                Thread.sleep(100);
                Socket s = new Socket("10.10.1.1", 4545);
                //DataInputStream din=new DataInputStream(s.getInputStream());  
                //BufferedInputStream bin = new BufferedInputStream(s.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
                DataOutputStream dout=new DataOutputStream(s.getOutputStream());  
                String message = "55.2,13.3,4.3";
                dout.writeUTF(message);
                System.out.println(reader.readLine());
                reader.close();
                dout.close();
                s.close();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}

public class LocalController{
    public static final double lr_max = 2.0;
    public static final double lr_min = -2.0;
    public static final float grad_max = 5.0f;
    public static final double power_min = 18;
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
                .setDefault(80).help("Control period in ms");
        parser.addArgument("--lr").type(Double.class)
                .setDefault(2.0).help("Gradient-to-powercap rate");
        parser.addArgument("--sampleperiod").type(Integer.class)
                .setDefault(20).help("Sample period in ms");
        parser.addArgument("--duration").type(Integer.class)
                .setDefault(60).help("Time duration in seconds");
        parser.addArgument("--alpha").type(Double.class)
                .setDefault(0.25).help("Adjustment rate to make PL and power closer");
        parser.addArgument("--tag").type(String.class)
                .setDefault("").help("CSV filename tag");
        parser.addArgument("--parent").type(String.class)
                .setDefault("").help("Parent controller address. Empty to run local control");
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
        PowerControllerThread pt = new PowerControllerThread((Integer)res.get("cap"),timeperiodms, (String)res.get("parent"));
        double[] curpl = pt.curpl.limits.clone();
        float[] powerusage = new float[curpl.length];
        double tolerance = 0.0;
        int epochs = (((Integer) res.get("duration")) * 1000) / timeperiodms;
        String policy = (String) res.get("policy");
        String tag = (String) res.get("tag");
        TraceCollectorThread t = new TraceCollectorThread(16, sampleperiodms, policy 
            +"-" + tag + "-" + (Integer)res.get("cap") + "W-raw.csv", timeperiodms);
        int num_pkg = t.num_sockets;
        int core_per_pkg = t.threadNum/t.num_sockets;
        MLModel endmodel = new MLModel(num_pkg, core_per_pkg , 6, "c220g2_power.pt", "c220g2_bips.pt");
        t.start();
        pt.start();
        String[] headers = {"Power", "Power Prediction", "Freq", "Gradient", "BIPS", 
            "BIPS Prediction", "Cur PL", "Next PL", "Util"};
        CSVRecord records = new CSVRecord(headers, num_pkg);
        records.printHeader(System.out);
        try{
        Thread.sleep(timeperiodms);
        } catch (Exception e){
            
        }
        float[] predictions = new float[num_pkg];
        float[] curperf = new float[num_pkg];
        float[] curutil = new float[num_pkg];
        float[][] core_bips = new float[num_pkg][core_per_pkg];
        float[][] core_cycles = new float[num_pkg][core_per_pkg]; // cycles per ms
        float[][] core_util = new float[num_pkg][core_per_pkg]; // cycles per ms
        
        float[] AvgBIPS = new float[num_pkg];
        float[] AvgPOW = new float[num_pkg];
        float[][] perfpredictions = new float[num_pkg][core_per_pkg];
        long curtimems = java.lang.System.currentTimeMillis();
        long basetime = curtimems;
        long nextPeriod = curtimems + timeperiodms;
        double total_curpower = 0;
        for (int epc = 0; epc < epochs; epc++){

            total_curpower = 0;
            curtimems = java.lang.System.currentTimeMillis();
            try{
                Thread.sleep(nextPeriod - curtimems);

            } catch (Exception e){
                System.err.println("error in sleep");
            }
            nextPeriod = nextPeriod + timeperiodms;
            totalcap = pt.totalcap;
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
                curutil[i] = 0;
                for (int j=0; j<core_per_pkg; j++){
                    
                    core_bips[i][j] = t.moving_input[i*core_per_pkg*9 + j*9 + 3];
                    
                    core_cycles[i][j] = t.moving_input[i*core_per_pkg*9 + j*9 + 4];//Already bcps
                    core_util[i][j] = core_cycles[i][j]*1e6f/PerfCounters.freqRange/t.moving_input[i*core_per_pkg*9 + j*9 + 1];
                    curperf[i] += core_bips[i][j];
                    curutil[i] += core_util[i][j]/core_per_pkg;
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
            /* 
            System.out.print("Cur power usage," + Arrays.toString(powerusage).replace('[', ' ').replace(']',' ') + 
                ",Freq," + Arrays.toString(avgfreqs).replace('[', ' ').replace(']',' ') +
                ",Prediction," + Arrays.toString(predictions).replace('[', ' ').replace(']',' ') +
                ",Gradients," + Arrays.toString(edp_gradients).replace('[', ' ').replace(']',' '));
            */
            records.newLine();
            

            float[] pkgbipspredictions = new float[num_pkg];
            for (int pkg=0; pkg<t.num_sockets; pkg++){
                pkgbipspredictions[pkg] = 0;
                for (int core=0; core<core_per_pkg; core++){
                    pkgbipspredictions[pkg] += perfpredictions[pkg][core];
                }
            }
            //System.out.print(",Cur perf," + Arrays.toString(curperf).replace('[', ' ').replace(']',' ') + 
            //    ",Bips Prediction," + Arrays.toString(pkgbipspredictions).replace('[', ' ').replace(']',' '));
            for (int pkg=0; pkg<t.num_sockets; pkg++){
                records.addRecord("Power:" + pkg, powerusage[pkg]);
                records.addRecord("Freq:" + pkg, avgfreqs[pkg]);
                records.addRecord("Power Prediction:" + pkg, predictions[pkg]);
                records.addRecord("Gradient:" + pkg, edp_gradients[pkg]);
                records.addRecord("BIPS:" + pkg, curperf[pkg]);
                records.addRecord("BIPS Prediction:" + pkg, pkgbipspredictions[pkg]);
                records.addRecord("Util:" + pkg, curutil[pkg]);
                

            }
            double[] newpl = curpl.clone();
            double pool = 0.0;
            final double min_pool = 2.0;
            double beta = curpl.length / (curpl.length - 0.99); //avoid divide-by-zero
            if (policy.equals("slurm")){
                
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
                
                for (int i = 0; i<curpl.length; i++){
                    double diff = curpl[i] - powerusage[i];
                    total_curpower += powerusage[i];
                    if (diff > 0){
                        pool += diff * 0.5 * beta;
                        newpl[i] -= diff*0.5*beta;
                        
                    }
                
                }
                //tolerance = tolerance*0.9 + (totalcap - total_curpower)*0.1;
                //System.out.print("Tolerance," + tolerance + ",");
                
                for (int i = 0; i<newpl.length; i++){
                    newpl[i] += (tolerance + pool) / newpl.length;
                }

            } else if (policy.equals("ml")){
                /* 
                double sum_newpl = 0;
                
                double grad_sum = 0;
                for (int i = 0; i<newpl.length; i++){
                    newpl[i] = curpl[i] - alpha*(curpl[i] - powerusage[i]) + lr*edp_gradients[i];
                    sum_newpl += newpl[i];
                    total_curpower += powerusage[i];
                    grad_sum +=  edp_gradients[i];
                }
                //tolerance = (tolerance + totalcap - total_curpower)*0.5;
                //System.err.println("Tolerance:" + tolerance + ", cap:" + totalcap + "curpower:" + total_curpower);
                if (sum_newpl > totalcap + tolerance){
                    double delta = totalcap + tolerance - total_curpower;
                    double newlr = delta / grad_sum;
                    for (int i = 0; i<newpl.length; i++){
                        newpl[i] = powerusage[i] + newlr*edp_gradients[i];
                    }
                }*/
                double sum_newpl = 0;
                double grad_sum=0;
                
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
                //tolerance = (tolerance + totalcap - total_curpower)*0.5;
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
                //tolerance = (tolerance + totalcap - total_curpower)*0.5;
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
                
                double grad_sum = 0;
                for (int i = 0; i<newpl.length; i++){
                    newpl[i] = curpl[i] - alpha*(curpl[i] - powerusage[i]) + lr*edp_gradients[i];
                    sum_newpl += newpl[i];
                    total_curpower += powerusage[i];
                    grad_sum +=  edp_gradients[i];
                }
                //tolerance = (tolerance + totalcap - total_curpower)*0.5;
                //tolerance = tolerance > 0 ? tolerance : 0;
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
                
                double grad_sum = 0;
                double epsilon = 2.0 * newpl.length;
                for (int i = 0; i<newpl.length; i++){
                    newpl[i] = curpl[i] - alpha*(curpl[i] - powerusage[i]) + lr*edp_gradients[i];
                    sum_newpl += newpl[i];
                    total_curpower += powerusage[i];
                    grad_sum +=  edp_gradients[i];
                }
                //tolerance = (tolerance + totalcap - total_curpower)*0.5;
                if (sum_newpl > totalcap + tolerance){
                    double delta = totalcap + tolerance - total_curpower + epsilon;
                    double newlr = delta / grad_sum;
                    for (int i = 0; i<newpl.length; i++){
                        newpl[i] = powerusage[i] + newlr*edp_gradients[i] - epsilon/newpl.length;
                    }
                }

            } else {
                //System.out.println(",Cur power limit," + arrToStr(curpl) +
                //",New power limit," + arrToStr(newpl) + ",Time," + (curtimems-basetime));
                records.addRecord("Time(ms)", curtimems-basetime);
                for (int pkg=0; pkg<t.num_sockets; pkg++){
                    records.addRecord("Cur PL:" + pkg, curpl[pkg]);
                    records.addRecord("Next PL:" + pkg, newpl[pkg]);
                    total_curpower += powerusage[pkg];
                }
                float[] bips_grads = endmodel.getBIPSGradients(freqs);
                float[] power_grads = endmodel.getPowerGradients(freqs);
                synchronized(pt.curpl){
                    for (int pkg=0; pkg<pt.curpl.numSocket; pkg++){
                        pt.curpl.limits[pkg] = newpl[pkg];
                        pt.curpl.usages[pkg] = powerusage[pkg];
                        pt.curpl.bips[pkg] = curperf[pkg];
                        pt.curpl.dBdP[pkg] = bips_grads[pkg]/(power_grads[pkg] + 1e-6);
                    }
                pt.curpl.notify();
                }
                records.printCSV(System.out);
                continue; //Assume fair policy
            }
            
            //System.out.println(",Cur power limit," + arrToStr(curpl) +
            //    ",New power limit," + arrToStr(newpl) + ",Time," + (curtimems-basetime));
            records.addRecord("Time(ms)", curtimems-basetime);
            for (int pkg=0; pkg<t.num_sockets; pkg++){
                records.addRecord("Cur PL:" + pkg, curpl[pkg]);
                records.addRecord("Next PL:" + pkg, newpl[pkg]);
                
            }
            curpl = newpl;
            float[] bips_grads = endmodel.getBIPSGradients(freqs);
            float[] power_grads = endmodel.getPowerGradients(freqs);
            synchronized(pt.curpl){
                for (int pkg=0; pkg<pt.curpl.numSocket; pkg++){
                    pt.curpl.limits[pkg] = newpl[pkg];
                    pt.curpl.usages[pkg] = powerusage[pkg];
                    pt.curpl.bips[pkg] = curperf[pkg];
                    pt.curpl.dBdP[pkg] = bips_grads[pkg]/(power_grads[pkg] + 1e-6);
                }
                pt.curpl.notify();
            }
            
            records.printCSV(System.out);
            
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
class NodeStatus{
    public double[] limits;
    public double[] usages;
    public int numSocket;
    public double[] bips;
    public double[] dBdP;
    public NodeStatus(int num_sockets){
        numSocket=num_sockets;
        limits = new double[num_sockets];
        usages = new double[num_sockets];
        bips = new double[num_sockets];
        dBdP = new double[num_sockets];
    }
}
class PowerControllerThread extends Thread{
    double[] pl1;
    double[] pl2;
    //public double[] curpl;
    public NodeStatus curpl;
    static final double pl2ratio = 1.2;
    public final double default_pl1 = 105.0;
    public final double default_pl2 = 126.0;
    public final int default_timewindow = 1000;
    int num_sockets;
    boolean running = true;
    String parentip;
    double totalcap;
    int timeperiodms;
    public PowerControllerThread(double powerlimit, int timeperiod, String parentip){
        timeperiodms=timeperiod;
        num_sockets = EnergyCheckUtils.GetSocketNum();
        pl1 = new double[num_sockets];
        pl2 = new double[num_sockets];
        curpl = new NodeStatus(num_sockets);
        this.parentip = parentip;
        this.totalcap = powerlimit;
        for (int s = 0; s<num_sockets; s++){
            double[] limitinfo = EnergyCheckUtils.GetPkgLimit(s);
            pl1[s] = limitinfo[0];
            pl2[s] = limitinfo[2];
            curpl.limits[s] = powerlimit / num_sockets;
		    System.err.println("Power limit1 of pkg " + s + ": " + limitinfo[0] + "\t timewindow1 :" + limitinfo[1]);
		    System.err.println("Power limit2 of pkg " + s + ": " + limitinfo[2] + "\t timewindow2 :" + limitinfo[3]);
            System.err.println("Trying to set running average timewindow to " + timeperiod + "ms");
            EnergyCheckUtils.SetRAPLTimeWindow(s, timeperiod);
            System.err.println("Trying to set running average limit to " + curpl.limits[s] + "W");
			EnergyCheckUtils.SetPkgLimit(s, curpl.limits[s], curpl.limits[s]*pl2ratio);
        }

    }
    public void terminate(){
        running = false;
    }
    public void run(){

        while(running){
            synchronized(curpl){
                try{
                    curpl.wait(1000);
                    for (int i=0; i<curpl.numSocket; i++){
                        EnergyCheckUtils.SetPkgLimit(i, curpl.limits[i], curpl.limits[i]*pl2ratio);
                    }
                    
                } catch (Exception e){
                    System.err.println("Timeout. Retrying");
                }
            }
            double totalBIPS = 0;
            double totaldBdP = 0;
            double total_curpower = 0;
            
            for (int pkg=0; pkg<curpl.numSocket; pkg++){
                    totalBIPS += curpl.bips[pkg];
                    totaldBdP += curpl.dBdP[pkg];
                    total_curpower += curpl.usages[pkg];
            }
            String message = String.format("%f,%f,%f",total_curpower,totalBIPS,totaldBdP);

            if (!parentip.equals("")){
                try{
                    
                    Socket s = new Socket(parentip, 4545);
                    s.setSoTimeout(timeperiodms/2);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    DataOutputStream dout=new DataOutputStream(s.getOutputStream());   
                    dout.writeUTF(message);
                    String newPKGLimit = reader.readLine();
                    totalcap = Double.parseDouble(newPKGLimit.split(":")[1]);
                    reader.close();
                    dout.close();
                    s.close();
                    
                } catch (Exception e) {
                    System.err.println("Cannot connect to cluster. Continuing...");
                    //e.printStackTrace();
                }
            }
            
        }

        for (int s = 0; s<num_sockets; s++){
            
            System.err.println("Reverting back to original limit");
			EnergyCheckUtils.SetPkgLimit(s, default_pl1, default_pl2);
            EnergyCheckUtils.SetRAPLTimeWindow(s, default_timewindow);
        }
    }
    
}