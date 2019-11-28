package smartphones_supply_chain_ontology.concepts;

public class PhabletSmartphone extends Smartphone{
	
	// Phablet smartphones are always going to have a screen of size 7 and a battery of charge 3000
	public PhabletSmartphone() {
		Screen screen = new Screen();
		screen.setSize("7");
		this.setScreen(screen);
		Battery battery = new Battery();
		battery.setCharge("3000");
		this.setBattery(battery);
	}
}
