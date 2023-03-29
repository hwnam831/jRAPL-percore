class PolyFunc {
    public float xval;
    public float[] coef;
    public int dim;
    public PolyFunc(int dim){
        this.dim = dim;
        this.coef = new float[dim+1];
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
}

public class MLModel {

	public static native void init(String fname_power, String fname_bips);
    //public static native void close();
    public static native float[] forward(float[] flat_input); // 4 power coefs + 2 bips coefs
    
    public int num_pkg;
    public int num_core;
    public int num_counters;
    public PolyFunc[][] power_func; //3rd-order poly
    public PolyFunc[][] bips_func; // ax+b
    public float[] adaptive_bias;
    public static final float adaptive_lr = 0.5f;
    
    //public native static 
    //public 

    static {

		System.loadLibrary("MLModel");

	}

    public MLModel(int num_pkg, int num_core, int num_counters, String fname_power, String fname_bips){
        this.num_pkg = num_pkg;
        this.num_core = num_core;
        this.num_counters = num_counters;
        init(fname_power, fname_bips);
        this.power_func = new PolyFunc[num_pkg][num_core];
        this.bips_func = new PolyFunc[num_pkg][num_core];
        this.adaptive_bias = new float[num_pkg];
        for (int p=0; p<num_pkg; p++){
            this.adaptive_bias[p] = 0;
            for (int c=0; c<num_core; c++){
                this.power_func[p][c] = new PolyFunc(3);
                this.bips_func[p][c] = new PolyFunc(1);
            }
        }
    }

    public void flat_to_poly(float[] coefs){
        int offset = 0;
        for (int p=0; p<this.num_pkg; p++){
            offset = p*this.num_core;
            for (int c=0; c<num_core; c++){
                this.power_func[p][c].coef[0] = coefs[(offset+c)*6 + 0];
                this.power_func[p][c].coef[1] = coefs[(offset+c)*6 + 1];
                this.power_func[p][c].coef[2] = coefs[(offset+c)*6 + 2];
                this.power_func[p][c].coef[3] = coefs[(offset+c)*6 + 3];
                this.bips_func[p][c].coef[0] = coefs[(offset+c)*6 + 4];
                this.bips_func[p][c].coef[1] = coefs[(offset+c)*6 + 5];
            }
        }
    }
    public void inference(float[] flat_input){
        flat_to_poly(forward(flat_input));
    }
    public float[] predict_power(float[][] freqs){
        float[] power = new float[this.num_pkg];
        for (int pkg=0; pkg<this.num_pkg; pkg++){
            power[pkg] = adaptive_bias[pkg];
        }
        for (int pkg=0; pkg<this.num_pkg; pkg++){
            for (int core=0; core<this.num_core; core++){
                power[pkg] += power_func[pkg][core].apply(freqs[pkg][core]);
            }
        }
        return power;
    }
    public void update_bias(float[] actual, float[] prediction){
        for (int i=0; i<adaptive_bias.length; i++){
            adaptive_bias[i] = (1-adaptive_lr) * adaptive_bias[i] +
                adaptive_lr*(actual[i] - prediction[i]);
        }
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