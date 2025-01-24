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
    float[][] active_coef_compiled; // compiled per core
    float[][] mcpi;
    float[][] ccpi;
    
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
        this.active_coef_compiled = new float[num_pkg][num_core];
        this.mcpi = new float[num_pkg][num_core];
        this.ccpi = new float[num_pkg][num_core];
        
    }


    public void compile(float[] raw_counters){
        
    }
    public float predict_power(float voltage){
        return 0.0f;
    }

    //pkg-level dbips/dp
    public float[] getdBIPSdP(float[][] freq){
        float[] grad = new float[this.num_pkg];
        return grad;
    }

    public static void main(String[] args){
        if (args.length < 1){
            System.out.println("usage: example-app <path-to-exported-script-module>\n");
        }
        //Test(args[0]);
        PolyFunc powerf = new PolyFunc(2);
        powerf.coef[0] = 1;
        powerf.coef[1] = 2;
        powerf.coef[2] = -1;

        System.out.println("f(4) = " + powerf.apply(4));
        System.out.println("f'(3) = " + powerf.derivative(3));

        init(args[0] + "_power.pt", args[0] + "_bips.pt");
        float[] flat = new float[1*1*2*10*9];
        for (int i=0; i<2*10*9; i++){
            flat[i] = (float)0.1;
        }
        float[] coefs = forward(flat);
        for (int i=0; i<12; i++){
            System.out.print(coefs[i] + ",");
        }
        System.out.println();
        long curtimems=java.lang.System.currentTimeMillis();
        for (int epc=0; epc<100; epc++){
            coefs = forward(flat);
        }
        System.out.println("Time per inference: " + (float)(java.lang.System.currentTimeMillis() - curtimems)/100);
    }
}