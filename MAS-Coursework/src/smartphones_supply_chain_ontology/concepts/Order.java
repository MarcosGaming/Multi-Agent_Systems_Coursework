package smartphones_supply_chain_ontology.concepts;

import jade.content.Concept;
import jade.content.onto.annotations.Slot;
import jade.core.AID;

public class Order implements Concept{
	
	private int quantity;
	private Smartphone smartphone;
	private int unitPrice;
	private AID aid;
	private int dueDate;
	private int penalty;
	
	@Slot(mandatory = true)
	public int getQuantity() {
		return quantity;
	}
	
	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
	
	@Slot(mandatory = true)
	public Smartphone getSmartphone() {
		return smartphone;
	}
	
	public void setSmartphone(Smartphone smartphone) {
		this.smartphone = smartphone;
	}
	
	@Slot(mandatory = true)
	public int getUnitPrice() {
		return unitPrice;
	}
	
	public void setUnitPrice(int unitPrice) {
		this.unitPrice = unitPrice;
	}
	
	@Slot(mandatory = true)
	public AID getAID() {
		return aid;
	}
	
	public void setAID(AID aid) {
		this.aid = aid;
	}
	
	@Slot(mandatory = true)
	public int getDueDate() {
		return dueDate;
	}
	
	public void setDueDate(int dueDate) {
		this.dueDate = dueDate;
	}
	
	@Slot(mandatory = true)
	public int getPenalty() {
		return penalty;
	}
	
	public void setPenalty(int penalty) {
		this.penalty = penalty;
	}

}
