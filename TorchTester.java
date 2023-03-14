public class TorchTester {

	public native static void Test(String fname);

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