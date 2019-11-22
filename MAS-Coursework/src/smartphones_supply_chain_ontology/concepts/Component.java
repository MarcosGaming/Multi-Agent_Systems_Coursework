package smartphones_supply_chain_ontology.concepts;

import jade.content.Concept;

public class Component implements Concept{
	
	private int price;
	
	public int getPrice(){
		return price;
	}
	
	public void setPrice(int price) {
		this.price = price;
	}
}
