package smartphones_supply_chain;

import java.util.ArrayList;

import jade.content.Concept;
import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import smartphones_supply_chain_ontology.SupplyChainOntology;
import smartphones_supply_chain_ontology.actions.SellSupplies;
import smartphones_supply_chain_ontology.concepts.Supplies;
import smartphones_supply_chain_ontology.predicates.NewDay;
import smartphones_supply_chain_ontology.predicates.SupplierDetails;
import smartphones_supply_chain_ontology.predicates.SupplierInformation;
import smartphones_supply_chain_ontology.predicates.SuppliesDelivered;

public class SupplierAgent extends Agent{

	private Codec codec = new SLCodec();
	private Ontology ontology = SupplyChainOntology.getInstance();
	private AID warehouseAID;
	private AID dayCoordinatorAID;
	private SupplierInformation supplierInformation;
	private ArrayList<SuppliesToDeliver> pendingDeliveries;
	
	// Initialize the agent
	protected void setup() {
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		// Register agent into the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Supplier");
		sd.setName(getLocalName() + "-supplier-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
		// Get the supplier information from the arguments
		Object[] args = this.getArguments();
		supplierInformation = (SupplierInformation) args[0];
		// Initialize pending deliveries list
		pendingDeliveries = new ArrayList<SuppliesToDeliver>();
		// Wait for other agents to initialize
		doWait(2000);
		// Add starter behaviours
		this.addBehaviour(new FindWarehouseBehaviour());
		this.addBehaviour(new FindDayCoordinatorBehaviour());
		this.addBehaviour(new DayCoordinatorWaiterBehaviour());
		this.addBehaviour(new ProvideDetailsBehaviour());
		this.addBehaviour(new ProcessSuppliesRequestsBehaviour());
	}
	
	// Called when agent is deleted
	protected void TakeDown() {
		// Bye message
		System.out.println("Agent " + this.getLocalName() + " is terminating.");
		// Deregister agent from the yellow pages
		try {
			DFService.deregister(this);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
	}
	
	// Behaviour to find the warehouse agent in the yellow pages
	private class FindWarehouseBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription warehouseTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Warehouse");
			warehouseTemplate.addServices(sd);
			try {
				warehouseAID = new AID();
				DFAgentDescription[] warehouseAgents = DFService.search(myAgent, warehouseTemplate);
				warehouseAID = warehouseAgents[0].getName();
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Behaviour to find the dayCoordinator agent in the yellow pages
	private class FindDayCoordinatorBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription dayCoordinatorTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("DayCoordinator");
			dayCoordinatorTemplate.addServices(sd);
			try {
				dayCoordinatorAID = new AID();
				DFAgentDescription[] dayCoordinatorAgents = DFService.search(myAgent, dayCoordinatorTemplate);
				dayCoordinatorAID = dayCoordinatorAgents[0].getName();
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Behaviour that waits for new day or end simulation calls
	public class DayCoordinatorWaiterBehaviour extends CyclicBehaviour{
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert string to java objects
					ContentElement ce = null;
					ce = getContentManager().extractContent(msg);
					// Every new day the supplier might send supplies to the warehouse
					if(ce instanceof NewDay) {
						// Add to the supplier the send supplies behaviour
						myAgent.addBehaviour(new SendSuppliesBehaviour());
					} else {
						// If the predicate is not NewDay then it is EndSimulation
						myAgent.doDelete();
					}
					
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
			} else {
				block();
			}
		}
	}
	
	// Behaviour that is going to provide to the sender with this agent details, this will only happen once
	public class ProvideDetailsBehaviour extends Behaviour{
		private boolean detailsSent = false;
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert string to java objects
					ContentElement ce = null;
					ce = getContentManager().extractContent(msg);
					// Make sure that the message contains the predicate supplier details
					if(ce instanceof SupplierDetails) {
						// Send a message to the manufacturer with the supplier information
						ACLMessage response = new ACLMessage(ACLMessage.INFORM);
						response.addReceiver(warehouseAID);
						response.setLanguage(codec.getName());
						response.setOntology(ontology.getName());
						try {
							// Convert java object to string
							getContentManager().fillContent(response, supplierInformation);
							myAgent.send(response);
						} catch (CodecException codece) {
							codece.printStackTrace();
						} catch (OntologyException oe) {
							oe.printStackTrace();
						}
						detailsSent = true;
					}
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
			} else {
				block();
			}
		}
		
		public boolean done() {
			return detailsSent;
		}
	}
	
	// Behaviour that is going to process requests to sell supplies
	public class ProcessSuppliesRequestsBehaviour extends CyclicBehaviour{
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert strings to java objects
					ContentElement ce = null;
					ce = getContentManager().extractContent(msg);
					if(ce instanceof Action) {
						Concept action = ((Action)ce).getAction();
						if(action instanceof SellSupplies) {
							SellSupplies sellSupplies = (SellSupplies) action;
							// Add the supplies to the list of pending deliveries
							SuppliesToDeliver suppliesToDeliver = new SuppliesToDeliver();
							suppliesToDeliver.setDaysLeftToDeliver(supplierInformation.getDeliveryTime());
							suppliesToDeliver.setSupplies(sellSupplies.getSupplies());
							pendingDeliveries.add(suppliesToDeliver);
						}
					}
				} catch (CodecException codece) {
					codece.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
			}
		}
	}
	
	// Behaviour that is going to send supplies if they are ready at the beginning of each day
	public class SendSuppliesBehaviour extends OneShotBehaviour{
		public void action() {
			for(SuppliesToDeliver delivery : pendingDeliveries) {
				int days = delivery.getDaysLeftToDeliver();
				days--;
				if(days == 0) {
					// Create predicate
					SuppliesDelivered suppliesDelivered = new SuppliesDelivered();
					suppliesDelivered.setSupplies(delivery.getSupplies());
					// Send supplies to warehouse
					ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
					msg.addReceiver(warehouseAID);
					msg.setLanguage(codec.getName());
					msg.setOntology(ontology.getName());
					try {
						getContentManager().fillContent(msg, suppliesDelivered);
						myAgent.send(msg);
					} catch (CodecException codece) {
						codece.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
					// Remove delivery from list of pending deliveries
					pendingDeliveries.remove(delivery);
				} else {
					// Update days left to deliver
					delivery.setDaysLeftToDeliver(days);
				}
			}
		}
	}
	
	// Class for the supplies that need to be sent
	private class SuppliesToDeliver{
		private Supplies supplies;
		private int daysLeftToDeliver;
		
		public void setSupplies(Supplies supplies) {
			this.supplies = supplies;
		}
		
		public Supplies getSupplies() {
			return supplies;
		}
		
		public void setDaysLeftToDeliver(int daysLeftToDeliver) {
			this.daysLeftToDeliver = daysLeftToDeliver;
		}
		
		public int getDaysLeftToDeliver() {
			return daysLeftToDeliver;
		}
		
	}
}
