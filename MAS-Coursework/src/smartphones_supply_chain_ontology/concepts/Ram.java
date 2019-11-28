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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + amount;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		Ram other = (Ram) obj;
		if (amount != other.amount)
			return false;
		return true;
	}
	
	

}
