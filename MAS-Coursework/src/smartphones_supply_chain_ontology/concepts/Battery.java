package smartphones_supply_chain_ontology.concepts;

import jade.content.onto.annotations.Slot;

public class Battery extends Component{
	
	private int charge;
	
	@Slot(mandatory = true)
	public int getCharge() {
		return charge;
	}
	
	public void setCharge(int charge) {
		this.charge = charge;
	}

}
