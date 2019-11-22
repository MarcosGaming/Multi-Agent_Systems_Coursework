package smartphones_supply_chain_ontology.actions;

import jade.content.AgentAction;
import jade.content.onto.annotations.Slot;
import smartphones_supply_chain_ontology.concepts.Supplies;

public class SellSupplies implements AgentAction{
	
	private Supplies supplies;
	
	@Slot(mandatory = true)
	public Supplies getSupplies() {
		return supplies;
	}
	
	public void setSupplies(Supplies supplies) {
		this.supplies = supplies;
	}
}
