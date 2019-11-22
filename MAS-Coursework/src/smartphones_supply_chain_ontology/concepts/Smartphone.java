package smartphones_supply_chain_ontology.concepts;

import jade.content.Concept;
import jade.content.onto.annotations.Slot;

public class Smartphone implements Concept{
	
	private Screen screen;
	private Storage storage;
	private Ram ram;
	private Battery battery;
	
	@Slot(mandatory = true)
	public Screen getScreen() {
		return screen;
	}
	
	protected void setScreen(Screen screen) {
		this.screen = screen;
	}
	
	@Slot(mandatory = true)
	public Storage getStorage() {
		return storage;
	}
	
	public void setStorage(Storage storage) {
		this.storage = storage;
	}
	
	@Slot(mandatory = true)
	public Ram getRam() {
		return ram;
	}
	
	public void setRam(Ram ram) {
		this.ram = ram;
	}
	
	@Slot(mandatory = true)
	public Battery getBattery() {
		return battery;
	}
	
	protected void setBattery(Battery battery) {
		this.battery = battery;
	}

}
