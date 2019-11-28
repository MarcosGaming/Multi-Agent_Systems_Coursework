package smartphones_supply_chain_ontology.predicates;

import jade.content.Predicate;
import jade.content.onto.annotations.Slot;

public class Payment implements Predicate{
	
	private int amount;
	
	@Slot(mandatory = true)
	public int getAmount() {
		return amount;
	}
	
	public void setAmount(int price) {
		this.amount = price;
	}
}
