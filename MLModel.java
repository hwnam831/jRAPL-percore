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
    public static native void close();
    public static native float[] forward(float[] flat_input); 
    public PolyFunc power_func; //3rd-order poly
    public PolyFunc bips_func; // ax+b

    //public native static 
    //public 

    static {

		//System.loadLibrary("jtorch");

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
    }
}