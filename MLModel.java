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

public class MLModel {

	public static native void init(String fname_power, String fname_bips);
    //public static native void close();
    public static native float[] forward(float[] flat_input); // 4 cpu power coefs + 2 dram power coefs + 2 bips coefs
    public float freq_max = 2.85f/4;
    public float freq_min = 0.95f/4;
    public int num_pkg;
    public int num_core;
    public int num_counters;
    public PolyFunc[][] power_func; //3rd-order poly
    public PolyFunc[][] dram_func; //3rd-order poly
    public PolyFunc[][] bips_func; // ax+b
    public float[] power_bias;
    public float[] dram_bias;
    public final float dram_base = 10;
    public float[][] bips_bias;
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
        this.dram_func = new PolyFunc[num_pkg][num_core];
        this.bips_func = new PolyFunc[num_pkg][num_core];
        this.power_bias = new float[num_pkg];
        this.dram_bias = new float[num_pkg];
        this.bips_bias = new float[num_pkg][num_core];
        for (int p=0; p<num_pkg; p++){
            this.power_bias[p] = 0;
            this.dram_bias[p] = 0;
            for (int c=0; c<num_core; c++){
                this.bips_bias[p][c] = 0.0f;
                this.power_func[p][c] = new PolyFunc(3);
                this.dram_func[p][c] = new PolyFunc(1);
                this.bips_func[p][c] = new PolyFunc(1);
            }
        }
    }

    public void flat_to_poly(float[] coefs){
        int offset = 0;
        for (int p=0; p<this.num_pkg; p++){
            offset = p*this.num_core;
            for (int c=0; c<num_core; c++){
                this.power_func[p][c].coef[0] = coefs[(offset+c)*8 + 0];
                this.power_func[p][c].coef[1] = coefs[(offset+c)*8 + 1];
                this.power_func[p][c].coef[2] = coefs[(offset+c)*8 + 2];
                this.power_func[p][c].coef[3] = coefs[(offset+c)*8 + 3];
                this.dram_func[p][c].coef[0] = coefs[(offset+c)*8 + 4];
                this.dram_func[p][c].coef[1] = coefs[(offset+c)*8 + 5];
                this.bips_func[p][c].coef[0] = coefs[(offset+c)*8 + 6];
                this.bips_func[p][c].coef[1] = coefs[(offset+c)*8 + 7];
            }
        }
    }
    public void inference(float[] flat_input){
        flat_to_poly(forward(flat_input));
    }
    public float[] predict_power(float[][] freqs){
        float[] power = new float[this.num_pkg];
        for (int pkg=0; pkg<this.num_pkg; pkg++){
            power[pkg] = power_bias[pkg];
        }
        for (int pkg=0; pkg<this.num_pkg; pkg++){
            for (int core=0; core<this.num_core; core++){
                power[pkg] += power_func[pkg][core].apply(freqs[pkg][core]);
            }
        }
        return power;
    }
    public float[] predict_dram(float[][] freqs){
        float[] power = new float[this.num_pkg];
        for (int pkg=0; pkg<this.num_pkg; pkg++){
            power[pkg] = dram_bias[pkg] + dram_base;
            //power[pkg] = 0;
        }
        for (int pkg=0; pkg<this.num_pkg; pkg++){
            for (int core=0; core<this.num_core; core++){
                power[pkg] += dram_func[pkg][core].apply(freqs[pkg][core]);
            }
        }
        return power;
    }

    public float[][] predict_perf(float[][] freqs){
        float[][] perf = new float[this.num_pkg][this.num_core];
        for (int pkg=0; pkg<this.num_pkg; pkg++){
            for (int core=0; core<this.num_core; core++){
                perf[pkg][core] = bips_bias[pkg][core] + bips_func[pkg][core].apply(freqs[pkg][core]);
            }
        }
        return perf;
    }
    public float[] getLocalEDPGradients(float[][] freqs, float[] pkgpower, float[] pkgbips){
        float[] gradients = new float[this.num_pkg];
        for (int p=0; p<this.num_pkg; p++){
            float grad_sum = 0.0f;
            for (int c=0; c<this.num_core; c++){
                float g_power = power_func[p][c].derivative(freqs[p][c]);
                float g_dram = dram_func[p][c].derivative(freqs[p][c]);
                float g_perf = bips_func[p][c].derivative(freqs[p][c]);
                float grad = 2*(pkgbips[p]/pkgpower[p])*(g_perf/(g_power+g_dram)) 
                    - (pkgbips[p]*pkgbips[p])/(pkgpower[p]*pkgpower[p]);
                if (grad < 0 && freqs[p][c] < freq_min){
                    grad = 0;
                } else if (grad > 0 && freqs[p][c] > freq_max){
                    grad = 0;
                }
                grad_sum += grad;
            }
            gradients[p] = grad_sum;
        }
        //1/E*D = B^2/P  G(B^2/P) = 2*B/P * (dB/df)*(df/dP) - B^2/P^2

        return gradients;
    }
    public float[] getLocalB2PGradients(float[][] freqs, float[] BIPS, float[] POW){
        float[] gradients = new float[this.num_pkg];
        
        //float[][] grad_power = new float[this.num_pkg][this.num_core];
        //float[][] val_power = new float[this.num_pkg][this.num_core];
        //float[][] grad_perf = new float[this.num_pkg][this.num_core];
        //float[][] val_perf = new float[this.num_pkg][this.num_core];
        float totalbips = 0.0f;
        float totalpower = 0.0f;
        for (int p=0; p<this.num_pkg; p++){
            for (int c=0; c<this.num_core; c++){
                float power = power_func[p][c].apply(freqs[p][c]) + power_bias[p]/this.num_core;
                float bips = bips_func[p][c].apply(freqs[p][c]) + bips_bias[p][c];
                totalpower += power;
                totalbips += bips;
            }
        }
        for (int p=0; p<this.num_pkg; p++){
            float grad_sum = 0.0f;
            for (int c=0; c<this.num_core; c++){
                float power = POW[p];
                float bips = BIPS[p];
                float g_power = power_func[p][c].derivative(freqs[p][c]);
                float g_perf = bips_func[p][c].derivative(freqs[p][c]);
                float grad = 2*(totalbips/totalpower)*(g_perf/g_power) - (totalbips*totalbips)/(totalpower*totalpower);
                if (grad < 0 && freqs[p][c] < freq_min){
                    grad = 0;
                } else if (grad > 0 && freqs[p][c] > freq_max){
                    grad = 0;
                }
                grad_sum += grad;
            }
            gradients[p] = grad_sum;
        }
        //1/E*D = B^2/P  G(B^2/P) = 2*B/P * (dB/df)*(df/dP) - B^2/P^2

        return gradients;
    }
    public float[] getGlobalEDPGradients(float[][] freqs, float totalbips, float totalpower){
        float[] gradients = new float[this.num_pkg];
        for (int p=0; p<this.num_pkg; p++){
            float grad_sum = 0.0f;
            for (int c=0; c<this.num_core; c++){
                float g_power = power_func[p][c].derivative(freqs[p][c]);
                float g_dram = dram_func[p][c].derivative(freqs[p][c]);
                float g_perf = bips_func[p][c].derivative(freqs[p][c]);
                float grad = 2*(totalbips/totalpower)*(g_perf/(g_power+g_dram)) - (totalbips*totalbips)/(totalpower*totalpower);
                if (grad < 0 && freqs[p][c] < freq_min){
                    grad = 0;
                } else if (grad > 0 && freqs[p][c] > freq_max){
                    grad = 0;
                }
                grad_sum += grad/this.num_pkg;
            }
            gradients[p] = grad_sum;
        }
        //1/E*D = B^2/P  G(B^2/P) = 2*B/P * (dB/df)*(df/dP) - B^2/P^2

        return gradients;
    }
    public float[] getPerfGradients(float[][] freqs){
        float[] gradients = new float[this.num_pkg];
        
        //float[][] grad_power = new float[this.num_pkg][this.num_core];
        //float[][] val_power = new float[this.num_pkg][this.num_core];
        //float[][] grad_perf = new float[this.num_pkg][this.num_core];
        //float[][] val_perf = new float[this.num_pkg][this.num_core];
        for (int p=0; p<this.num_pkg; p++){
            float grad_sum = 0.0f;
            PolyFunc pkg_bips = new PolyFunc(1);
            PolyFunc pkg_power = new PolyFunc(3);
            for (int c=0; c<this.num_core; c++){
                float f = freqs[p][c];

                float bips = bips_func[p][c].apply(f);
                float g_power = power_func[p][c].derivative(f);
                float g_perf = bips_func[p][c].derivative(f);
                grad_sum += (g_perf/g_power);
                pkg_bips.add(bips_func[p][c]);
                pkg_power.add(power_func[p][c]);
            }
            System.err.println("PKG " + p + " Perf:" + pkg_bips.polyString() + "\tPower:" + pkg_power.polyString());
            gradients[p] = grad_sum;
        }
        //dB^2/dP = 2B*dB/df * df/dP

        return gradients;
    }

    public float[] getBIPSGradients(float[][] freqs){
        float[] gradients = new float[this.num_pkg];
        
        //float[][] grad_power = new float[this.num_pkg][this.num_core];
        //float[][] val_power = new float[this.num_pkg][this.num_core];
        //float[][] grad_perf = new float[this.num_pkg][this.num_core];
        //float[][] val_perf = new float[this.num_pkg][this.num_core];
        for (int p=0; p<this.num_pkg; p++){
            float grad_sum = 0.0f;
            PolyFunc pkg_bips = new PolyFunc(1);
            for (int c=0; c<this.num_core; c++){
                float f = freqs[p][c];

                float bips = bips_func[p][c].apply(f);
                float g_perf = bips_func[p][c].derivative(f);
                if (freqs[p][c] > freq_max){
                    g_perf = 0;
                }
                grad_sum += g_perf;
                pkg_bips.add(bips_func[p][c]);
            }
            //System.err.println("PKG " + p + " Perf:" + pkg_bips.polyString() + "\tPower:" + pkg_power.polyString());
            gradients[p] = grad_sum;
        }
        //dB^2/dP = 2B*dB/df * df/dP

        return gradients;
    }
    //Only CPU power grads
    public float[] getPowerGradients(float[][] freqs){
        float[] gradients = new float[this.num_pkg];
        
        //float[][] grad_power = new float[this.num_pkg][this.num_core];
        //float[][] val_power = new float[this.num_pkg][this.num_core];
        //float[][] grad_perf = new float[this.num_pkg][this.num_core];
        //float[][] val_perf = new float[this.num_pkg][this.num_core];
        for (int p=0; p<this.num_pkg; p++){
            float grad_sum = 0.0f;
            for (int c=0; c<this.num_core; c++){
                float f = freqs[p][c];
                float power = power_func[p][c].apply(f);

                float g_power = power_func[p][c].derivative(f);
                grad_sum += g_power;
            }
            gradients[p] = grad_sum;
        }
        //d(B/P)/dP =(dB/df * df/dP)/P - B/P^2

        return gradients;
    }
    public float[] getDRAMGradients(float[][] freqs){
        float[] gradients = new float[this.num_pkg];
        
        //float[][] grad_power = new float[this.num_pkg][this.num_core];
        //float[][] val_power = new float[this.num_pkg][this.num_core];
        //float[][] grad_perf = new float[this.num_pkg][this.num_core];
        //float[][] val_perf = new float[this.num_pkg][this.num_core];
        for (int p=0; p<this.num_pkg; p++){
            float grad_sum = 0.0f;
            for (int c=0; c<this.num_core; c++){
                float f = freqs[p][c];

                float g_power = dram_func[p][c].derivative(f);
                grad_sum += g_power;
            }
            gradients[p] = grad_sum;
        }
        //d(B/P)/dP =(dB/df * df/dP)/P - B/P^2

        return gradients;
    }

    public float[] getEnergyGradients(float[][] freqs){
        float[] gradients = new float[this.num_pkg];
        
        //float[][] grad_power = new float[this.num_pkg][this.num_core];
        //float[][] val_power = new float[this.num_pkg][this.num_core];
        //float[][] grad_perf = new float[this.num_pkg][this.num_core];
        //float[][] val_perf = new float[this.num_pkg][this.num_core];
        for (int p=0; p<this.num_pkg; p++){
            float grad_sum = 0.0f;
            for (int c=0; c<this.num_core; c++){
                float f = freqs[p][c];
                float power = power_func[p][c].apply(f);
                float bips = bips_func[p][c].apply(f);
                float g_power = power_func[p][c].derivative(f);
                float g_perf = bips_func[p][c].derivative(f);
                grad_sum += (g_perf/g_power)/power - bips/(power*power);
            }
            gradients[p] = grad_sum;
        }
        //d(B/P)/dP =(dB/df * df/dP)/P - B/P^2

        return gradients;
    }

    public void update_bias(float[] actual, float[] prediction){
        for (int i=0; i<power_bias.length; i++){
            power_bias[i] = power_bias[i] +
                adaptive_lr*(actual[i] - prediction[i]);
        }
    }
    public void update_dram_bias(float[] actual, float[] prediction){
        for (int i=0; i<dram_bias.length; i++){
            dram_bias[i] = dram_bias[i] +
                adaptive_lr*(actual[i] - prediction[i]);
        }
    }
    public void update_perf_bias(float[][] actual, float[][] prediction){
        for (int i=0; i<bips_bias.length; i++){
            for (int j=0; j<bips_bias[0].length; j++)
            bips_bias[i][j] = bips_bias[i][j] +
                adaptive_lr*(actual[i][j] - prediction[i][j]);
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