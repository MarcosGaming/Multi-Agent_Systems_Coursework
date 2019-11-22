package smartphones_supply_chain_ontology.concepts;

import jade.content.onto.annotations.Slot;

public class Ram extends Component{
	
	private int amount;
	
	@Slot(mandatory = true)
	public int getAmount() {
		return amount;
	}
	
	public void setAmount(int amount) {
		this.amount = amount;
	}

}
