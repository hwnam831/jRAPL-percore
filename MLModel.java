class 

public class MLModel {

	public native static void init(String fname);
    public native static void close();
    public native static 

    static {

		System.loadLibrary("jtorch");

	}
    public static void main(String[] args){
        if (args.length < 1){
            System.out.println("usage: example-app <path-to-exported-script-module>\n");
        }
        Test(args[0]);
    }
}