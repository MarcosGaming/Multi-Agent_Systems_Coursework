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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + price;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Component other = (Component) obj;
		if (price != other.price)
			return false;
		return true;
	}
	
	
}
