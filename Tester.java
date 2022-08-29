public class Tester{

    public static void main(String[] args) {

        double pl1 = -1;
        double pl2 = -1;
		if (args.length >= 1){
			pl1 = Double.parseDouble(args[0]);
		}

        if (args.length >= 2){
			pl2 = Double.parseDouble(args[1]);
		} else {
            pl2 = pl1;
        }

		double[] limitinfo = EnergyCheckUtils.GetPkgLimit(0);

		System.err.println("Power limit1 of pkg: " + limitinfo[0] + "\t timewindow1 :" + limitinfo[1]);
		System.err.println("Power limit2 of pkg: " + limitinfo[2] + "\t timewindow2 :" + limitinfo[3]);

		if (pl2 > 0){
			System.err.println("Trying to set short term limit to " + pl2 + "W");
			EnergyCheckUtils.SetPkgLimit(0, pl1, pl2);
			double[] limitinfo2 = EnergyCheckUtils.GetPkgLimit(0);
			System.err.println("Power limit1 of pkg: " + limitinfo2[0] + "\t timewindow1 :" + limitinfo2[1]);
			System.err.println("Power limit2 of pkg: " + limitinfo2[2] + "\t timewindow2 :" + limitinfo2[3]);
		}
    }
}