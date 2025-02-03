import java.io.*;

class PolyFunc {
    public float xval;
    public float[] coef;
    public int dim;
    public PolyFunc(int dim){
        this.dim = dim;
        this.coef = new float[dim+1];
        for (float c: coef){
            c=0;
        }
    }
    public float apply(float x){
        float val = 1;
        float output = 0;
        for (float c: coef){
            output += c*val;
            val = val * x;
        }
        return output;
    }
    public float derivative(float x){
        float val = 1;
        float output = 0;
        for (int i=1; i<this.dim+1; i++){
            output += coef[i]*i*val;
            val = val * x;
        }
        return output;
    }
    public String polyString(){
        String line = ""+coef[0];
        for (int i=1; i<coef.length; i++){
            line += " + " + coef[i] + "x^" + i;
        }
        return line;
    }
    public void add(PolyFunc p){
        for (int i=0; i<this.dim+1; i++){
            coef[i] += p.coef[i];
        }
    }
}

public class PPEPModel {

	//public static native void init(String fname_power, String fname_bips);
    
    //public static native void close();
    //public static native float[] forward(float[] flat_input); // 4 cpu power coefs + 2 dram power coefs + 2 bips coefs
    
    public float freq_max = 2.8f/4;
    public float freq_min = 0.9f/4;
    public int num_pkg;
    public int num_core;
    // uop, bmiss, cmiss, bips, bcps
    public int num_counters = 5;
    
    public float[] idle_coefs;
    public float[] vf_poly;
    public float[] active_coefs;
    public float[] predicted_power;
    public float[] dBIPSdP; // store it per core
    //float[][] active_coef_compiled; // compiled per core
    //float[][] mcpi;
    //float[][] ccpi;
    //float[][] cur_freq;
    //float[][] cur_volt;
    
    //public native static 
    //public 

    public static float[] readFile(String fname, int count){
        float[] arr = new float[count];

        try (BufferedReader br = new BufferedReader(new FileReader(fname))) {
            String line;
            for (int i =0; i<count; i++){
                line = br.readLine();

                try {
                    float number = Float.parseFloat(line.trim());
                    arr[i] = number;
                } catch (NumberFormatException e) {
                    System.err.println("Skipping invalid line: " + line);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return arr;
    }
    public PPEPModel(int num_pkg, int num_core, String fname_idle, String fname_vfpoly, String fname_active){
        this.num_pkg = num_pkg;
        this.num_core = num_core;

        this.idle_coefs = readFile(fname_idle, 4);
        this.vf_poly = readFile(fname_vfpoly, 3);
        this.active_coefs = readFile(fname_active, 5);
        //this.active_coef_compiled = new float[num_pkg][num_core];
        //this.mcpi = new float[num_pkg][num_core];
        //this.ccpi = new float[num_pkg][num_core];
        //this.cur_freq = new float[num_pkg][num_core];
        //this.cur_volt = new float[num_pkg][num_core];
        this.predicted_power = new float[num_pkg];
        this.dBIPSdP = new float[num_pkg];
        
    }

    // raw_counters[core_per_socket*n_sockets][5+n_counters]
    // voltage,freq,temp,inst,cycle, ldm-stalls,cache-misses,branch-misses,uops
    public void compile(float[][] coreCtrs){
        for (int p=0; p<this.num_pkg; p++){
            float pkg_idle_power = 0.0f;
            float pkg_dyn_power = 0.0f;
            this.dBIPSdP[p] = 0;
            for (int c=0; c<this.num_core; c++){
                float[] ctrs = coreCtrs[p*this.num_core+c];
                float freq = ctrs[1];
                //this.cur_freq[p][c] = freq;
                float voltage = ctrs[0];
                //this.cur_volt[p][c] = voltage;
                float temp = ctrs[2];
                float bips = ctrs[3];
                float bcps = ctrs[4];
                float util = bcps/freq;
                float ldm_stalls = ctrs[5];
                float cache_misses = ctrs[6];
                float branch_misses = ctrs[7];
                float uops = ctrs[8];

                float mcpi = ldm_stalls/bips;
                float cpi = bcps/bips;
                float ccpi = cpi - mcpi;
                //this.mcpi[p][c] = mcpi;
                //this.ccpi[p][c] = ccpi;
                //coef: uop, bmiss, cmiss, bips, bcps
                float active_coef_compiled = 
                    this.active_coefs[0]*uops + this.active_coefs[1]*branch_misses + 
                    this.active_coefs[2]*cache_misses + this.active_coefs[3]*bips + this.active_coefs[4]*bcps;

                //compute power prediction  
                float idle_power = this.idle_coefs[0]*voltage*voltage*voltage + 
                    this.idle_coefs[1]*voltage*voltage + this.idle_coefs[2]*voltage + this.idle_coefs[3];
                
                float dyn_power = active_coef_compiled * (1.25f*voltage*voltage*voltage + voltage);
                idle_power /= this.num_core; //currently, idle power is a per-package model
                
                pkg_idle_power += idle_power;
                pkg_dyn_power += dyn_power;

                //compute the gradients
                float dBdf = (util * ccpi) / (cpi*cpi) + util*(1-util)/cpi;
                float dVdf = 2*this.vf_poly[0] * freq + this.vf_poly[1];
                float dVdB = dVdf / dBdf;
                float dPdyndB = dyn_power/bips + active_coef_compiled * (2.4f*voltage*voltage + 1) * dVdB;
                float dPidledB = (3*this.idle_coefs[0]*voltage*voltage + 2*this.idle_coefs[1]*voltage + this.idle_coefs[2]) * dVdB / this.num_core;
                this.dBIPSdP[p] += 1/(dPdyndB + dPidledB)/this.num_core;
                //this.dBIPSdP[p] += 1/dPdyndB;
                  
                
            }
            this.predicted_power[p] = pkg_dyn_power + pkg_idle_power;
        }
    }

    public float[] getB2PGradients(float[] power, float[] bips){
        float[] grads = new float[this.num_pkg];
        for (int p=0; p<this.num_pkg; p++){
            float bpp = bips[p]/power[p];
            grads[p] = 2*bpp * this.dBIPSdP[p] - bpp*bpp;
        }
        return grads;
        
    }

    public float[] getGlobalB2PGradients(float totalpower, float totalbips){
        float[] grads = new float[this.num_pkg];
        for (int p=0; p<this.num_pkg; p++){
            float bpp = totalbips/totalpower;
            grads[p] = 2*bpp * this.dBIPSdP[p] - bpp*bpp;
        }
        return grads;
    }

    
    public static void main(String[] args){
        
        //Test(args[0]);
        PPEPModel mymodel = new PPEPModel(2, 10, "ppep/idlemodel.txt", "ppep/vfpoly.txt","ppep/activecoef.txt");
        
        float[][] ctrs = new float[20][9];
        for (int core=0; core < 20; core++){
            ctrs[core][0] = 0.83f; // volt
            ctrs[core][1] = 2.5f; // freq
            ctrs[core][2] = 30; // temp
            ctrs[core][3] = 6.1f; // bips
            ctrs[core][4] = 2.5f; // bcps
            ctrs[core][5] = 0.2f; // ldm
            ctrs[core][6] = 0.0002f; // cmiss
            ctrs[core][7] = 0.0001f; // bmiss
            ctrs[core][8] = 5.7f; // uops
            //ctrs[core] = PPEPModel.readFile("testinput.txt", 9);
        }

        mymodel.compile(ctrs);
        //System.out.println("Predicted power: " + mymodel.predicted_power[0] + "," + mymodel.predicted_power[1]
        //     + "; Gradient: " + mymodel.dBIPSdP[0] + "," + mymodel.dBIPSdP[1]);

        try (BufferedReader reader = new BufferedReader(new FileReader("testinput.csv"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Split the line by commas
                String[] values = line.split(",");
                // Parse each value as a float
                for (int i=0; i<9; i++) {
                    try {
                        float number = Float.parseFloat(values[i].trim());
                        for (int core=0; core < 20; core++)
                            ctrs[core][i] = number;
                        
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Skipping invalid number: " + values[i]);
                    }
                }
                mymodel.compile(ctrs);
                float[] bips = new float[2];
                bips[0] = ctrs[0][3]*10;
                bips[1] = ctrs[10][3]*10;
                float[] b2pgrads = mymodel.getB2PGradients(mymodel.predicted_power, bips);
                System.out.println("Predicted power," + mymodel.predicted_power[0] + "," + mymodel.predicted_power[1]
                    + ", Gradient," + mymodel.dBIPSdP[0] + "," + mymodel.dBIPSdP[1] +
                    ",B2PGrad," + b2pgrads[0] + "," + b2pgrads[1] );
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}