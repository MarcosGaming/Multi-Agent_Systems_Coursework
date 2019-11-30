package smartphones_supply_chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.HashMap;
import java.util.LinkedList;

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
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import smartphones_supply_chain_ontology.SupplyChainOntology;
import smartphones_supply_chain_ontology.actions.CalculateOrderCost;
import smartphones_supply_chain_ontology.actions.PrepareOrderToAssemble;
import smartphones_supply_chain_ontology.actions.SellSupplies;
import smartphones_supply_chain_ontology.concepts.Battery;
import smartphones_supply_chain_ontology.concepts.Component;
import smartphones_supply_chain_ontology.concepts.Order;
import smartphones_supply_chain_ontology.concepts.Ram;
import smartphones_supply_chain_ontology.concepts.Screen;
import smartphones_supply_chain_ontology.concepts.Storage;
import smartphones_supply_chain_ontology.concepts.Supplies;
import smartphones_supply_chain_ontology.predicates.NewDay;
import smartphones_supply_chain_ontology.predicates.NoMoreOrdersToday;
import smartphones_supply_chain_ontology.predicates.NoMoreSuppliesToday;
import smartphones_supply_chain_ontology.predicates.OrdersReady;
import smartphones_supply_chain_ontology.predicates.PredictedOrderCost;
import smartphones_supply_chain_ontology.predicates.SupplierDetails;
import smartphones_supply_chain_ontology.predicates.SupplierInformation;
import smartphones_supply_chain_ontology.predicates.SuppliesDelivered;
import smartphones_supply_chain_ontology.predicates.WarehouseExpenses;
import smartphones_supply_chain_ontology.predicates.WarehouseExpensesToday;

public class WarehouseAgent extends Agent{

	private Codec codec = new SLCodec();
	private Ontology ontology = SupplyChainOntology.getInstance();
	
	private AID manufacturerAID;
	private AID dayCoordinatorAID;
	private AID[] suppliersAID;
	
	private ArrayList<Supplier> suppliers;
	private ArrayList<Order> readyOrders;
	private ArrayList<PendingOrder> pendingOrders;
	private HashMap<Component,Integer> stock;
	private ArrayList<SuppliesNeeded> suppliesToBeOrdered;
	
	private static final int maxNumberSmartphonesAssemblePerDay = 50;
	private static final int perComponentDailyStorageCost = 5;
	private int numbSmartphonesAssembledToday;
	
	private int dailySuppliesPurchasedCost;
	private int dailyPenaltiesCost;
	private int dailyStorageCost;
	
	// Initialise the agent
	protected void setup() {
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		// Register agent into the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Warehouse");
		sd.setName(getLocalName() + "-warehouse-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
		// Initialise variables
		suppliers = new ArrayList<Supplier>();
		readyOrders = new ArrayList<Order>();
		pendingOrders = new ArrayList<PendingOrder>();
		stock = new HashMap<Component, Integer>();
		suppliesToBeOrdered = new ArrayList<SuppliesNeeded>();
		// Wait for other agents to initialise
		doWait(2000);
		// Add starter behaviours
		this.addBehaviour(new FindManufacturerBehaviour());
		this.addBehaviour(new FindSuppliersBehaviour());
		this.addBehaviour(new FindDayCoordinatorBehaviour());
		this.addBehaviour(new DayCoordinatorWaiterBehaviour());
	}
	
	// Called when agent is deleted
	protected void takeDown() {
		// Bye message
		System.out.println("Agent " + this.getLocalName() + " is terminating.");
		// Deregister agent from the yellow pages
		try {
			DFService.deregister(this);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
	}
	
	// Behaviour to find the manufacturer agent in the yellow pages
	private class FindManufacturerBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription manufacturerTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Manufacturer");
			manufacturerTemplate.addServices(sd);
			try {
				manufacturerAID = new AID();
				DFAgentDescription[] manufacturerAgents = DFService.search(myAgent, manufacturerTemplate);
				manufacturerAID = manufacturerAgents[0].getName();
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Behaviour to find the supplier agents in the yellow pages
	private class FindSuppliersBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription supplierTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Supplier");
			supplierTemplate.addServices(sd);
			try {
				DFAgentDescription[] supplierAgents = DFService.search(myAgent, supplierTemplate);
				int size = supplierAgents.length;
				suppliersAID = new AID[size];
				for(int i = 0; i < size; i++) {
					suppliersAID[i] = supplierAgents[i].getName();
				}
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
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchSender(dayCoordinatorAID));
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert string to java objects
					ContentElement ce = getContentManager().extractContent(msg);;
					// Every new day the warehouse is going to carry out a series of operations
					if(ce instanceof NewDay) {
						// Reset variables
						numbSmartphonesAssembledToday = 0;
						dailySuppliesPurchasedCost = 0;
						dailyPenaltiesCost = 0;
						dailyStorageCost = 0;
						// Add sequential behaviour
						SequentialBehaviour dailyActivity = new SequentialBehaviour();
						if(suppliers.isEmpty()) {
							dailyActivity.addSubBehaviour(new GetSupplierDetailsBehaviour());
						}
						dailyActivity.addSubBehaviour(new ProcessSuppliesDeliveredBehaviour());
						dailyActivity.addSubBehaviour(new ProcessPendingOrdersBehaviour());
						dailyActivity.addSubBehaviour(new UpdatePendingOrdersTimesAndCalculatePenaltiesBehaviour());
						dailyActivity.addSubBehaviour(new ProcessManufacturerOrderRequestsBehaviour());
						dailyActivity.addSubBehaviour(new RequestSuppliesToSuppliersBehaviour());
						dailyActivity.addSubBehaviour(new SendReadyOrdersBehaviour());
						dailyActivity.addSubBehaviour(new CalculateStorageCostBehaviour());
						dailyActivity.addSubBehaviour(new SendDailyWarehouseExpensesBehaviour());
						myAgent.addBehaviour(dailyActivity);
					} else {
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
	
	// Behaviour that is going to ask the suppliers for their components, prices and delivery times
	private class GetSupplierDetailsBehaviour extends Behaviour{
		private int step = 0;
		int numResponsesReceived = 0;
		
		public void action() {
			switch(step) {
			// Send query message to suppliers
			case 0:
				// Create predicate
				SupplierDetails supplierDetails = new SupplierDetails();
				// Send message
				ACLMessage msg = new ACLMessage(ACLMessage.QUERY_IF);
				for(int i = 0; i < suppliersAID.length; i++) {
					msg.addReceiver(suppliersAID[i]);
				}
				msg.setLanguage(codec.getName());
				msg.setOntology(ontology.getName());
				try {
					// Transform java objects to strings
					getContentManager().fillContent(msg, supplierDetails);
					myAgent.send(msg);
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
				step++;
				break;
			// Receive suppliers response
			case 1:
				MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				ACLMessage msg1 = myAgent.receive(mt);
				if(msg1 != null) {
					try {
						// Transform strings to java objects
						ContentElement ce = getContentManager().extractContent(msg1);
						if(ce instanceof SupplierInformation) {
							SupplierInformation supplierInformation = (SupplierInformation) ce;
							// Store the supplier information internally
							suppliers.add(new Supplier(supplierInformation.getComponents(), supplierInformation.getDeliveryTime(), msg1.getSender()));
							numResponsesReceived++;
							if(numResponsesReceived == suppliersAID.length) {
								step++;
							}
						} else {
							myAgent.postMessage(msg1);
						}
					} catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				} else {
					block();
				}
				break;
			}
			
		}
		
		public boolean done() {
			return step == 2;
		}
	}
	
	// Behaviour that is going to process the supplies received by the supplier
	private class ProcessSuppliesDeliveredBehaviour extends Behaviour{
		private int numbSuppliersDone = 0;
		
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Transform strings to java objects
					ContentElement ce = getContentManager().extractContent(msg);
					if(ce instanceof SuppliesDelivered) {
						// Store supplies internally
						SuppliesDelivered suppliesDelivered = (SuppliesDelivered) ce;
						int quantityPerComponent = suppliesDelivered.getSupplies().getComponentsQuantity();
						for(Component component : suppliesDelivered.getSupplies().getComponents()) {
							// Check if the same component with the same price is already in stock map
							if(stock.containsKey(component)) {
								// Increase the quantity
								int quantity = quantityPerComponent + stock.get(component);
								stock.replace(component, quantity);
							} else {
								// Add the mapElement to the stock
								stock.put(component, quantityPerComponent);
							}
						}
					} else if (ce instanceof NoMoreSuppliesToday) {
						// Each supplier is going to send the NoMoreseSuppliesToday predicate after sending all the supplies
						numbSuppliersDone++;
					} else {
						myAgent.postMessage(msg);
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
			return numbSuppliersDone == suppliersAID.length;
		}
	}
	
	// Behaviour that is going to process pending orders
	private class ProcessPendingOrdersBehaviour extends OneShotBehaviour{
		public void action() {
			// The pending orders need to be processed from first to last as they are reordered each time a new order is added to them
			ArrayList<PendingOrder> ordersToRemove = new ArrayList<PendingOrder>();
			for(PendingOrder pendingOrder : pendingOrders) {
				Order order = pendingOrder.getPendingOrder();
				// If the order required amount of smartphones cannot be assembled do not process any more orders
				if(numbSmartphonesAssembledToday + order.getQuantity() > maxNumberSmartphonesAssemblePerDay) {
					break;
				}
				// Check if the components needed are in stock
				Screen screen = order.getSmartphone().getScreen();
				Storage storage = order.getSmartphone().getStorage();
				Ram ram = order.getSmartphone().getRam();
				Battery battery = order.getSmartphone().getBattery();
				if(stock.containsKey(screen) && stock.containsKey(storage) && stock.containsKey(ram) && stock.containsKey(battery)) {
					// Check if there are enough components to assemble the order
					int screenAvailables = stock.get(order.getSmartphone().getScreen());
					int storageAvailables = stock.get(order.getSmartphone().getStorage());
					int ramAvailables = stock.get(order.getSmartphone().getRam());
					int batteryAvailables = stock.get(order.getSmartphone().getBattery());
					int requiredQuantity = order.getQuantity();
					if(screenAvailables >= requiredQuantity && storageAvailables >= requiredQuantity && ramAvailables >= requiredQuantity && batteryAvailables >= requiredQuantity) {
						// Update number of smartphones assembled today
						numbSmartphonesAssembledToday += requiredQuantity;
						// Add order to ready orders list
						readyOrders.add(order);
						// Update stock
						stock.replace(screen, screenAvailables - requiredQuantity);
						stock.replace(storage, storageAvailables - requiredQuantity);
						stock.replace(ram, ramAvailables - requiredQuantity);
						stock.replace(battery, batteryAvailables - requiredQuantity);
						// Remove current order from pending orders
						ordersToRemove.add(pendingOrder);
					} else {
						// If there are not enough components of any type to assemble the current order do not process any more orders
						break; 
					}
				} else {
					// If the components needed to assemble the current order are not in stock do not process any more orders
					break; 
				}
			}
			// Remove ready orders from pending orders
			if(!ordersToRemove.isEmpty()) {
				pendingOrders.removeAll(ordersToRemove);
			}
		}
	}
	
	// Behaviour that is going to update the due date and left days to assembly of the pending orders that have not been assembled today and calculate any penalties before adding new orders
	private class UpdatePendingOrdersTimesAndCalculatePenaltiesBehaviour extends OneShotBehaviour{
		public void action() {
			for(PendingOrder pendingOrder : pendingOrders) {
				pendingOrder.setDaysLeftToAssemble(pendingOrder.getDaysLeftToAssemble() - 1);
				pendingOrder.getPendingOrder().setDueDate(pendingOrder.getPendingOrder().getDueDate() - 1);
				if(pendingOrder.getPendingOrder().getDueDate() < 0) {
					dailyPenaltiesCost += pendingOrder.getPendingOrder().getPenalty();
				}
			}
		}
	}
	
	// Behaviour that is going to calculate the costs of an order and prepare orders to be assembled
	private class ProcessManufacturerOrderRequestsBehaviour extends Behaviour{
		private boolean allOrdersProcessed = false;
		private PredictedOrderInformation predictedOrderInformation = new PredictedOrderInformation();
		public void action() {
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.REQUEST), MessageTemplate.MatchPerformative(ACLMessage.INFORM));
			ACLMessage msg = myAgent.receive();
			if(msg != null) {
				try {
					// Transform strings to java objects
					ContentElement ce = getContentManager().extractContent(msg);
					if(ce instanceof Action) {
						Concept action = ((Action)ce).getAction();
						if(action instanceof CalculateOrderCost) {
							CalculateOrderCost orderCost = (CalculateOrderCost) action;
							// Create predicate
							PredictedOrderCost predictedOrderCost = new PredictedOrderCost();
							// Store predicted order information in case that the manufacturer accepts the order so the calculation is only done once
							predictedOrderInformation = calculateOrderInformation(orderCost.getOrder());
							predictedOrderCost.setCost(predictedOrderInformation.getMinimumPredictedCost());
							// Send to the manufacturer a predicted cost for the order
							ACLMessage msgOrderCost = new ACLMessage(ACLMessage.INFORM);
							msgOrderCost.addReceiver(manufacturerAID);
							msgOrderCost.setLanguage(codec.getName());
							msgOrderCost.setOntology(ontology.getName());
							try {
								// Transform java objects to strings
								getContentManager().fillContent(msgOrderCost, predictedOrderCost);
								myAgent.send(msgOrderCost);
							} catch (CodecException codece) {
								codece.printStackTrace();
							} catch (OntologyException oe) {
								oe.printStackTrace();
							}
							
						} else if(action instanceof PrepareOrderToAssemble) {
							// Add order to pending orders
							PendingOrder pendingOrder = new PendingOrder();
							pendingOrder.setOrder(predictedOrderInformation.getOrder());
							pendingOrder.setDaysLeftToAssemble(predictedOrderInformation.getPredictedAssemblytime());
							pendingOrders.add(predictedOrderInformation.getPredictedPositionInPendingOrdersList(), pendingOrder);
							// To minimise storage costs, when there are more than one supplier to order components from, different orders of supplies for different days are going to be made
							int quantity = predictedOrderInformation.getOrder().getQuantity();
							int time = predictedOrderInformation.getPredictedAssemblytime();
							for(Map.Entry<Supplier, ArrayList<Component>> entry : predictedOrderInformation.getComponentsToOrderFromSuppliers().entrySet()) {
								SuppliesNeeded suppliesNeeded = new SuppliesNeeded(entry.getKey(), entry.getValue(), quantity, time);
								suppliesToBeOrdered.add(suppliesNeeded);
							}
						}
					} else if(ce instanceof NoMoreOrdersToday) {
						allOrdersProcessed = true;
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
			return allOrdersProcessed;
		}
	}
	
	// Behaviour that is going to request supplies to the suppliers
	private class RequestSuppliesToSuppliersBehaviour extends OneShotBehaviour{
		public void action() {
			// Minimise warehouse expenses by making sure that all the supplies for an order arrive the same day
			ArrayList<SuppliesNeeded> suppliesToRemove = new ArrayList<SuppliesNeeded>();
			for(SuppliesNeeded supplies : suppliesToBeOrdered) {
				// Check if the supplies should be ordered today
				if(supplies.getTimeLeftToRequestDelivery() == supplies.getSupplier().getDeliveryTime()) {
					// Create agent action
					SellSupplies sellSupplies = new SellSupplies();
					Supplies s = new Supplies();
					s.setComponents(supplies.getComponents());
					s.setComponentsQuantity(supplies.getQuantityPerComponent());
					sellSupplies.setSupplies(s);
					// Create wrapper
					Action request = new Action();
					request.setAction(sellSupplies);
					request.setActor(supplies.getSupplier().getSupplierAID());
					// Send message to supplier requesting the supplies
					ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
					msg.addReceiver(supplies.getSupplier().getSupplierAID());
					msg.setLanguage(codec.getName());
					msg.setOntology(ontology.getName());
					try {
						// Transform java objects to strings
						getContentManager().fillContent(msg, request);
						myAgent.send(msg);
					} catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
					// Get the cost of the supplies and add it to the daily supplies purchased cost
					for(Component component : supplies.getComponents()) {
						dailySuppliesPurchasedCost = component.getPrice() * supplies.getQuantityPerComponent();
					}
					// Add supplies to the list of supplies to remove
					suppliesToRemove.add(supplies);
				} else {
					// Decrease the time left to request delivery
					supplies.setTimeLeftToRequestDelivery(supplies.getTimeLeftToRequestDelivery() - 1);
				}
			}
			// remove supplies
			suppliesToBeOrdered.removeAll(suppliesToRemove);
		}
	}
	// Behaviour that is going to send orders ready to the manufacturer
	private class SendReadyOrdersBehaviour extends OneShotBehaviour{
		public void action() {
			// Create predicate
			OrdersReady ordersToSend = new OrdersReady();
			ordersToSend.setOrders(readyOrders);
			// Send message
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(manufacturerAID);
			msg.setLanguage(codec.getName());
			msg.setOntology(ontology.getName());
			try {
				// Transform java objects to strings
				getContentManager().fillContent(msg, ordersToSend);
				myAgent.send(msg);
			} catch (CodecException ce) {
				ce.printStackTrace();
			} catch (OntologyException oe) {
				oe.printStackTrace();
			}
			// Clear list
			readyOrders.clear();
		}
	}
	
	// Behaviour that is going to calculate the costs of the storage 
	private class CalculateStorageCostBehaviour extends OneShotBehaviour{
		public void action() {
			for(Map.Entry<Component,Integer> entry : stock.entrySet()) {
				dailyStorageCost = entry.getValue() * perComponentDailyStorageCost;
			}
		}
	}
	
	// Behaviour that is going to send the daily warehouse expenses to the manufacturer when asked
	private class SendDailyWarehouseExpensesBehaviour extends Behaviour{
		int step = 0;
		public void action() {
			switch(step) {
			// Wait for query message from manufacturer
			case 0:
				MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF);
				ACLMessage msg = myAgent.receive(mt);
				if(msg != null) {
					try {
						// Transform strings to java objects
						ContentElement ce = getContentManager().extractContent(msg);
						if(ce instanceof WarehouseExpensesToday) {
							step++;
						}
					} catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				} else {
					block();
				}
				break;
			// Send daily warehouse expenses to the manufacturer
			case 1:
				// Create predicate
				WarehouseExpenses expenses = new WarehouseExpenses();
				expenses.setPenaltiesCost(dailyPenaltiesCost);
				expenses.setStorageCost(dailyStorageCost);
				expenses.setSuppliesCost(dailySuppliesPurchasedCost);
				// Send expenses
				ACLMessage response = new ACLMessage(ACLMessage.INFORM);
				response.addReceiver(manufacturerAID);
				response.setLanguage(codec.getName());
				response.setOntology(ontology.getName());
				try {
					// Transform java objects to strings
					getContentManager().fillContent(response, expenses);
					myAgent.send(response);
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
				step++;
				break;
			}
		}
		
		public boolean done() {
			return step == 2;
		}
	}
	
	private class Supplier{
		private List<Component> componentsAvailable;
		private int deliveryTime;
		private AID supplierAID;
		
		public Supplier(List<Component> componentsAvailable, int deliveryTime, AID supplierAID) {
			this.componentsAvailable = componentsAvailable;
			this.deliveryTime = deliveryTime;
			this.supplierAID = supplierAID;
		}
		
		public int getDeliveryTime() {
			return deliveryTime;
		}
		
		public AID getSupplierAID() {
			return supplierAID;
		}
		
		public Component getComponentUsingProperty(Component componentToFind) {
			for(Component component : componentsAvailable) {
				if(componentToFind instanceof Screen && component instanceof Screen) {
					Screen screenToFind = (Screen) componentToFind;
					Screen screen = (Screen) component;
					if(screen.getSize().equals(screenToFind.getSize()))
					{
						return screen;
					}
				} else if(componentToFind instanceof Storage && component instanceof Storage) {
					Storage storageToFind = (Storage) componentToFind;
					Storage storage = (Storage) component;
					if(storage.getCapacity().equals(storageToFind.getCapacity()))
					{
						return storage;
					}
				} else if(componentToFind instanceof Ram && component instanceof Ram) {
					Ram ramToFind = (Ram) componentToFind;
					Ram ram = (Ram) component;
					if(ram.getAmount().equals(ramToFind.getAmount()))
					{
						return ram;
					}
				} else if(componentToFind instanceof Battery && component instanceof Battery) {
					Battery batteryToFind = (Battery) componentToFind;
					Battery battery = (Battery) component;
					if(battery.getCharge().equals(batteryToFind.getCharge()))
					{
						return battery;
					}
				}
			}
			return null;
		}
	}
	
	private class SuppliesNeeded{
		private Supplier supplier;
		private ArrayList<Component> components;
		private int quantityPerComponent;
		private int timeLeftToRequestDelivery;
		
		public SuppliesNeeded(Supplier supplier, ArrayList<Component> components, int quantityPerComponent, int timeLeftToRequestDelivery) {
			this.supplier = supplier;
			this.components = components;
			this.quantityPerComponent = quantityPerComponent;
			this.timeLeftToRequestDelivery = timeLeftToRequestDelivery;
		}
		
		public Supplier getSupplier() {
			return supplier;
		}
		
		public ArrayList<Component> getComponents(){
			return components;
		}
		
		public int getQuantityPerComponent() {
			return quantityPerComponent;
		}
		
		public int getTimeLeftToRequestDelivery() {
			return timeLeftToRequestDelivery;
		}
		
		public void setTimeLeftToRequestDelivery(int time) {
			this.timeLeftToRequestDelivery = time;
		}
		
	}
	
	private class PendingOrder{
		private Order order;
		private int daysLeftToAssemble;
		
		public void setOrder(Order order) {
			this.order = order;
		}
		
		public Order getPendingOrder() {
			return order;
		}
		
		public void setDaysLeftToAssemble(int daysLeftToAssemble) {
			this.daysLeftToAssemble = daysLeftToAssemble;
		}
		
		public int getDaysLeftToAssemble() {
			return daysLeftToAssemble;
		}
	}
	
	private class PredictedOrderInformation{
		private int minimumPredictedCost;
		private int assemblyTime;
		private int positionInPendingOrdersList;
		private HashMap<Supplier, ArrayList<Component>> componentsToOrderFromSupplier;
		private Order order;
		
		public int getMinimumPredictedCost() {
			return minimumPredictedCost;
		}
		
		public void setMinimumPredictedCost(int minimumPredictedCost) {
			this.minimumPredictedCost = minimumPredictedCost;
		}
		
		public int getPredictedAssemblytime() {
			return assemblyTime;
		}
		
		public void setPredictedAssemblyTime(int predictedAssemblyTime) {
			this.assemblyTime = predictedAssemblyTime;
		}
		
		public int getPredictedPositionInPendingOrdersList() {
			return positionInPendingOrdersList;
		}
		
		public void setPredictedPositionInPendingOrdersList(int predictedPositionInPendingOrdersList) {
			this.positionInPendingOrdersList = predictedPositionInPendingOrdersList;
		}
		
		public HashMap<Supplier, ArrayList<Component>> getComponentsToOrderFromSuppliers(){
			return componentsToOrderFromSupplier;
		}
		
		public void setComponentsToOrderFromSuppliers(HashMap<Supplier, ArrayList<Component>> componentsToOrderFromSuppliers) {
			this.componentsToOrderFromSupplier = componentsToOrderFromSuppliers;
		}
		
		public Order getOrder() {
			return order;
		}
		
		public void setOrder(Order order) {
			this.order = order;
		}
	}
	
	/* Method that is going to calculate the minimum cost of an order, when it would be ready, how the pending order list should be updated
	 * and retrieve from which suppliers the components for this order should be requested
	 */
	private PredictedOrderInformation calculateOrderInformation(Order order) {
		// The information to be returned
		PredictedOrderInformation predictedOrderInformation = new PredictedOrderInformation();
		int minimumPredictedCost = 0;
		int predictedPositionInPendingOrdersList = pendingOrders.size();
		// Get the components specification
		ArrayList<Component> componentsToFind = new ArrayList<Component>();
		componentsToFind.add(order.getSmartphone().getScreen());
		componentsToFind.add(order.getSmartphone().getStorage());
		componentsToFind.add(order.getSmartphone().getRam());
		componentsToFind.add(order.getSmartphone().getBattery());
		// Create prices for each of the suppliers and select the cheapest option
		for(Supplier supplier : suppliers) {
			// Store prices from the suppliers components to update the order components prices
			int screenPrice = 0;
			int storagePrice = 0;
			int ramPrice = 0;
			int batteryPrice = 0;
			// Hahsmap with the components to order
			HashMap<Supplier, ArrayList<Component>> predictedComponentsToOrderFromSupplier = new HashMap<Supplier, ArrayList<Component>>();
			// Reset predicted cost and delivery time
			int predictedCost = 0;
			int deliveryTime = supplier.getDeliveryTime();
			// Get what would be the predicted cost of the all components together
			for(Component component : componentsToFind) {
				int componentPrice;
				Supplier selectedSupplier = null;
				// If the supplier does not have the component search for the cheapest alternative in other suppliers
				if(supplier.getComponentUsingProperty(component) == null) {
					int cheapestAlternativeComponentPrice = 0;
					for(Supplier alternativeSupplier : suppliers) {
						// Skip alternative supplier if is the same as the current supplier or if it does not have the component either
						if(alternativeSupplier == supplier) {
							continue;
						} else if(alternativeSupplier.getComponentUsingProperty(component) != null) {
							int alternativeComponentPrice = alternativeSupplier.getComponentUsingProperty(component).getPrice();
							// Update cheapest alternative price
							if( cheapestAlternativeComponentPrice == 0 || alternativeComponentPrice < cheapestAlternativeComponentPrice) {
								cheapestAlternativeComponentPrice = alternativeComponentPrice;
								selectedSupplier = alternativeSupplier;
								// Update delivery time based on the supplier that will take longer to deliver a component
								if(deliveryTime < alternativeSupplier.deliveryTime) {
									deliveryTime = alternativeSupplier.deliveryTime;
								}
							}
						}
					}
					componentPrice = cheapestAlternativeComponentPrice;
				} else {
					componentPrice = supplier.getComponentUsingProperty(component).getPrice();
					selectedSupplier = supplier;
				}
				// Update order prices
				if(component instanceof Screen) {
					screenPrice = componentPrice;
				} else if(component instanceof Storage) {
					storagePrice = componentPrice;
				} else if(component instanceof Ram) {
					ramPrice = componentPrice;
				} else {
					batteryPrice = componentPrice;
				}
				// Update predicted cost
				predictedCost += componentPrice * order.getQuantity();
				// Add supplier-component to the hashmap with all the suppliers and the components that should be bought from them
				if(predictedComponentsToOrderFromSupplier.containsKey(selectedSupplier)) {
					ArrayList<Component> components = predictedComponentsToOrderFromSupplier.get(selectedSupplier);
					components.add(selectedSupplier.getComponentUsingProperty(component));
					predictedComponentsToOrderFromSupplier.replace(selectedSupplier, components);
				} else {
					ArrayList<Component> components = new ArrayList<Component>();
					components.add(selectedSupplier.getComponentUsingProperty(component));
					predictedComponentsToOrderFromSupplier.put(selectedSupplier, components);
				}
			}
			// Go through pending orders to get when the new order could get assembled and calculate the final assembly time
			int assemblyTime = deliveryTime;
			for(PendingOrder pendingOrder : pendingOrders) {
				if(assemblyTime == pendingOrder.getDaysLeftToAssemble()) {
					int numbSmartphonesToBeAssembledThatDay = 0;
					// Check how many smartphones are going to be assembled that day
					int i = pendingOrders.indexOf(pendingOrder);
					while(i < pendingOrders.size() && pendingOrders.get(i).getDaysLeftToAssemble() == pendingOrder.getDaysLeftToAssemble()) {
						numbSmartphonesToBeAssembledThatDay += pendingOrders.get(i).getPendingOrder().getQuantity();
						i++;
					}
					// If the new order could be assembled that day the assembly time stays the same
					if(numbSmartphonesToBeAssembledThatDay + order.getQuantity() <= maxNumberSmartphonesAssemblePerDay) {
						predictedPositionInPendingOrdersList = pendingOrders.indexOf(pendingOrder);
						break;
					}
					else {
						assemblyTime++;
					}
					// If the new order could be assembled before another order without affecting the rest the assembly time stays the same
				} else if(assemblyTime < pendingOrder.getDaysLeftToAssemble()) {
					predictedPositionInPendingOrdersList = pendingOrders.indexOf(pendingOrder);
					break;
				}
			}
			// Add penalties based on the calculated assembly time
			if(assemblyTime > order.getDueDate()) {
				predictedCost += (assemblyTime - order.getDueDate()) * order.getPenalty();
			}
			// If the new predicted cost is less than the minimum, update the minimum cost, the order and the predicted order information
			if(minimumPredictedCost == 0 || predictedCost < minimumPredictedCost) {
				// Update minimum predicted cost
				minimumPredictedCost = predictedCost;
				// Update the price of the order components
				order.getSmartphone().getScreen().setPrice(screenPrice);
				order.getSmartphone().getStorage().setPrice(storagePrice);
				order.getSmartphone().getRam().setPrice(ramPrice);
				order.getSmartphone().getBattery().setPrice(batteryPrice);
				// Update predicted order information
				predictedOrderInformation.setOrder(order);
				predictedOrderInformation.setMinimumPredictedCost(minimumPredictedCost);
				predictedOrderInformation.setPredictedAssemblyTime(assemblyTime);
				predictedOrderInformation.setPredictedPositionInPendingOrdersList(predictedPositionInPendingOrdersList);
				predictedOrderInformation.setComponentsToOrderFromSuppliers(predictedComponentsToOrderFromSupplier);
			}
		}
		return predictedOrderInformation;
	}
	
}
