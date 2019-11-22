package smartphones_supply_chain_ontology.predicates;

import jade.content.Predicate;
import jade.content.onto.annotations.Slot;

public class Payment implements Predicate{
	
	private int price;
	
	@Slot(mandatory = true)
	public int getPrice() {
		return price;
	}
	
	public void setPrice(int price) {
		this.price = price;
	}
}
