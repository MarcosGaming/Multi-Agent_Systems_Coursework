package smartphones_supply_chain_ontology.concepts;

public class SmallSmartphone extends Smartphone{
	
	// Small smartphones are always going to have a screen of size 5 and a battery of charge 2000
	public SmallSmartphone() {
		Screen screen = new Screen();
		screen.setSize(5);
		this.setScreen(screen);
		Battery battery = new Battery();
		battery.setCharge(2000);
		this.setBattery(battery);
	}
}
