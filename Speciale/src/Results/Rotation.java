package Results;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.omg.CORBA.SystemException;

import Data.Data;
import Data.Demand;
import Data.DistanceElement;
import Data.Port;
import Data.VesselClass;
import Graph.*;
import RotationFlow.RotationEdge;
import RotationFlow.RotationGraph;

public class Rotation {
	private int id;
	private VesselClass vesselClass;
	private ArrayList<Node> rotationNodes;
	private ArrayList<Edge> rotationEdges;
	private double speed;
	private int noOfVessels;
	private int distance;
	private static AtomicInteger idCounter = new AtomicInteger();
	private boolean active;
	private Graph mainGraph;
	private Graph rotationGraph;
	private Rotation subRotation;

	public Rotation(){
	}

	public Rotation(VesselClass vesselClass, Graph mainGraph, int id) {
		super();
		if(id == -1){
			this.id = idCounter.getAndIncrement();
		} else {
			this.id = id;
		}
		this.vesselClass = vesselClass;
		this.rotationNodes = new ArrayList<Node>();
		this.rotationEdges = new ArrayList<Edge>();
		this.active = true;
		this.speed = 0;
		this.noOfVessels = 0;
		this.distance = 0;
		this.mainGraph = mainGraph;
		this.rotationGraph = null;
		this.subRotation = null;
		mainGraph.getResult().addRotation(this);
		//		calculateSpeed();
	}

	public void createRotationGraph(){
		this.rotationGraph = new Graph(this);
	}

	public void setSubRotation(Rotation r){
		this.subRotation = r;
	}

	public void findRotationFlow() throws InterruptedException{
		System.out.println("Checking rotation no. " + id);
		rotationGraph.runMcf();
//		removeWorstPort();
//		rotationGraph.testAddPort();
//		rotationGraph.removeWorstPort();
		insertBestPort();
//		rotationGraph.findFlow();
		rotationGraph.getMcf().saveODSol("ODSol.csv", rotationGraph.getDemands());
	}

	public boolean insertBestPort() throws InterruptedException{
		boolean madeChange = false;
		rotationGraph.runMcf();
//		int bestObj = rotationGraph.getResult().getObjective();
		int bestObj = -Integer.MAX_VALUE;
		System.out.println("Org obj: " + bestObj);

		Port bestOrgPort = null;
		Port bestFeederPort = null;
		Node bestOrgDepNode = null;
		Node bestOrgNextPortArrNode = null;
		//		ArrayList<Node> bestInsertNodes = new ArrayList<Node>();
		Edge worstFromFeeder = null;
		Edge worstToFeeder = null;
		Edge worstNextSail = null;
		for(int i=rotationGraph.getEdges().size()-1; i >= 0; i--){
			Edge e = rotationGraph.getEdges().get(i);
			Edge toFeeder = null;
			Edge nextSail = null;
//			if(e.isFeeder()){
//				if(e.getToPortUNLo().equals("NLRTM") && e.getFromPortUNLo().equals("EGPSD")){
//					int edgeCost = e.getCost();
//					throw new RuntimeException("EGPSD -> NLRTM : " + edgeCost);
//				}
//			}
			if(e.isFeeder() && e.getFromNode().isFromCentroid()){
				Port orgPort = e.getToNode().getPort();
//				System.out.println("Feeder from port: " + e.getFromPortUNLo() + " to rotationPort: " + e.getToPortUNLo());
				Port feederPort = e.getFromNode().getPort();
				if(feederPort.getDraft() < vesselClass.getDraft()){
					System.out.println("Draft at feederPort is too low");
					continue;
				}
				Node orgDepNode = e.getToNode();
				for(Edge outEdge : orgDepNode.getPrevEdge().getFromNode().getOutgoingEdges()){
					if(outEdge.isFeeder() && outEdge.getToNode().getPort().getUNLocode().equals(feederPort.getUNLocode())){
						toFeeder = outEdge;
						break;
					}
//					if(outEdge.isFeeder()){
//						System.out.println("feeder edge outEdge going from: " + outEdge.getFromPortUNLo() + " to feeder port: " + outEdge.getToPortUNLo() );
//					}	
				}
				nextSail = e.getToNode().getNextEdge();
				Node orgNextPortArrNode = nextSail.getToNode();
				nextSail.setInactive();
				if(toFeeder != null){
					toFeeder.setInactive();
				}
				e.setInactive();
				ArrayList<Node> insertNodes = rotationGraph.tryInsertMakeNodes(this, orgPort, feederPort);
				ArrayList<Edge> insertEdges = rotationGraph.tryInsertMakeEdges(this, insertNodes, orgDepNode, orgNextPortArrNode);
				if(enoughVessels()){
					calcOptimalSpeed();
				} else {
					System.out.println("Not enough ships!!!");
					continue;
				}
				rotationGraph.runMcf();
				int obj = rotationGraph.getResult().getObjective();
				System.out.println("Try insert obj: " + obj);
				if(obj > bestObj){
					bestObj = obj;
//					bestInsertNodes = insertNodes;
					System.out.println("IMPROVEMENT");
					bestOrgPort = orgPort;
					bestFeederPort = feederPort;
					bestOrgDepNode = orgDepNode;
					bestOrgNextPortArrNode = orgNextPortArrNode;
					madeChange = true;
					worstFromFeeder = e;
					worstToFeeder = toFeeder;
					worstNextSail = nextSail;
//					throw new RuntimeException("IMPROVEMENT");
				}
				rotationGraph.undoTryInsertMakeNodes(insertNodes);
//				rotationGraph.undoTryInsertMakeEdges(insertEdges);
				nextSail.setActive();
				if(toFeeder != null){
					toFeeder.setActive();
				}
				e.setActive();
				//				rotationGraph.tryInsertPort(this, nextSail, feederPort);
			}
		}
		if(madeChange){
			int prevNoInRot = bestOrgDepNode.getPrevEdge().getPrevEdge().getNoInRotation();
			incrementNoInRotation(prevNoInRot);
			incrementNoInRotation(prevNoInRot);
			incrementNoInRotation(prevNoInRot);

			ArrayList<Node> newRotNodes = implementInsertPortNodes(rotationGraph, bestOrgPort, bestFeederPort);
			implementInsertPortEdges(rotationGraph, newRotNodes, bestOrgDepNode, bestOrgNextPortArrNode, prevNoInRot);

			rotationGraph.deleteEdge(worstFromFeeder);
			if(worstToFeeder != null){
				rotationGraph.deleteEdge(worstToFeeder);	
			}
			rotationGraph.deleteEdge(worstNextSail);

			System.out.println("MADE CHANGE");
//			mainGraph.deleteEdge(worstFromFeeder);
//			mainGraph.deleteEdge(worstToFeeder);
//			mainGraph.deleteEdge(worstNextSail);

		}
		return madeChange;
	}

	private void implementInsertPortEdges(Graph graph, ArrayList<Node> newNodes, Node bestOrgDepNode, Node bestOrgNextPortArrNode, int prevNoInRot) {
		ArrayList<Edge> insertEdges = new ArrayList<Edge>();

		Node newFeederArrNode = newNodes.get(0);
		Node newFeederDepNode = newNodes.get(1);
		Node newOrgArrNode = newNodes.get(2);
		Node newOrgDepNode = newNodes.get(3);
		DistanceElement newToFeederPortDist = Data.getBestDistanceElement(bestOrgDepNode.getPort(), newFeederArrNode.getPort(), this.getVesselClass());
		DistanceElement newFromFeederPortDist = Data.getBestDistanceElement(newFeederDepNode.getPort(), newOrgArrNode.getPort(), this.getVesselClass());
		DistanceElement newOrgSailDist = Data.getBestDistanceElement(newOrgDepNode.getPort(), bestOrgNextPortArrNode.getPort(), this.getVesselClass());
		Edge newToFeederPort = graph.createRotationEdge(this, bestOrgDepNode, newFeederArrNode, 0, this.getVesselClass().getCapacity(), prevNoInRot+1, newToFeederPortDist);
		//		Edge newMainToFeederPort = mainGraph.createRotationEdge(this, bestOrgDepNode, newFeederArrNode, 0, this.getVesselClass().getCapacity(), prevNoInRot+1, newToFeederPortDist);
		Edge newFromFeederPort = graph.createRotationEdge(this, newFeederDepNode, newOrgArrNode, 0, this.getVesselClass().getCapacity(), prevNoInRot+2, newFromFeederPortDist);
		//		Edge newMainFromFeederPort = mainGraph.createRotationEdge(this, newFeederDepNode, newOrgArrNode, 0, this.getVesselClass().getCapacity(), prevNoInRot+2, newFromFeederPortDist);
		Edge newOrgSail = graph.createRotationEdge(this, newOrgDepNode, bestOrgNextPortArrNode, 0, this.getVesselClass().getCapacity(), prevNoInRot+3, newOrgSailDist);
		//		Edge newMainOrgSail = mainGraph.createRotationEdge(this, newOrgDepNode, bestOrgNextPortArrNode, 0, this.getVesselClass().getCapacity(), prevNoInRot+3, newOrgSailDist);

		Edge newRotFeederDwell = graph.createRotationEdge(this, newFeederArrNode, newFeederDepNode, 0, this.getVesselClass().getCapacity(), -1, null);
		//		Edge newMainFeederDwell = mainGraph.createRotationEdge(this, newFeederArrNode, newFeederDepNode, 0, this.getVesselClass().getCapacity(), -1, null);
		ArrayList<Edge> transhipmentFeederPort = graph.createTransshipmentEdges(newRotFeederDwell);
		//		ArrayList<Edge> mainTranshipmentFeederPort = mainGraph.createTransshipmentEdges(newMainFeederDwell);
		//		insertEdges.addAll(transhipmentFeederPort);
		ArrayList<Edge> loadUnloadFeederPort = graph.createLoadUnloadEdges(newRotFeederDwell);
		//		ArrayList<Edge> mainLoadUnloadFeederPort = mainGraph.createLoadUnloadEdges(newMainFeederDwell);

		//		insertEdges.addAll(loadUnloadFeederPort);

		Edge newRotOrgDwell = graph.createRotationEdge(this, newOrgArrNode, newOrgDepNode, 0, this.getVesselClass().getCapacity(), -1, null);
		//		Edge newMainOrgDwell = mainGraph.createRotationEdge(this, newOrgArrNode, newOrgDepNode, 0, this.getVesselClass().getCapacity(), -1, null);
		ArrayList<Edge> transhipmentNewOrgPort = graph.createTransshipmentEdges(newRotOrgDwell);
		//		ArrayList<Edge> mainTranshipmentNewOrgPort = mainGraph.createTransshipmentEdges(newMainOrgDwell);
		//		insertEdges.addAll(transhipmentNewOrgPort);
		ArrayList<Edge> loadUnloadNewOrgPort = graph.createLoadUnloadEdges(newRotOrgDwell);
		//		ArrayList<Edge> mainLoadUnloadNewOrgPort = mainGraph.createLoadUnloadEdges(newMainOrgDwell);
		//		insertEdges.addAll(loadUnloadNewOrgPort);

		this.calcOptimalSpeed();

	}

	private ArrayList<Node> implementInsertPortNodes(Graph graph, Port bestOrgPort, Port bestFeederPort) {
		ArrayList<Node> newNodes = graph.tryInsertMakeNodes(this, bestOrgPort, bestFeederPort);
		return newNodes;
	}

	public boolean removeWorstPort() throws InterruptedException{
		System.out.println("Now looking at rotation no. " + id);
		boolean madeChange = false;
		rotationGraph.runMcf();
		int bestObj = rotationGraph.getResult().getObjective();
//		System.out.println("Org obj: " + bestObj);

		Edge worstDwellEdge = null;
		for(int i=rotationGraph.getEdges().size()-1; i>=0; i--){
			Edge e = rotationGraph.getEdges().get(i);
			if(e.isDwell() && isRelevantToRemove(e)){
				ArrayList<Edge> handledEdges = rotationGraph.tryRemovePort(e, this);
				rotationGraph.runMcf();
				int obj = rotationGraph.getResult().getObjective();
//				System.out.println("Try obj: " + obj + " by removing " + e.getFromPortUNLo());
				if(obj > bestObj){
					bestObj = obj;
					worstDwellEdge = e;
					madeChange = true;
				}
				rotationGraph.undoTryRemovePort(handledEdges, this);
			}
		}
		if(madeChange){
			implementRemoveWorstPort(worstDwellEdge);
			rotationGraph.runMcf();
		}
		return madeChange;
	}
	
	private boolean isRelevantToRemove(Edge e){
		Port p = e.getFromNode().getPort();
		if(!e.isDwell()){
			return false;
		}
		int portCalls = 0;
		for(Node n : rotationNodes){
			if(n.isDeparture()){
				if(n.getPort().equals(p)){
					portCalls++;
				}
			}
		}
		if(portCalls > 1){
			return true;
		}
		int unload = e.getLoad() - e.getPrevEdge().getLoad();
		int load = e.getNextEdge().getLoad() - e.getLoad();
		int activity = unload + load;
		//TODO: Parameter - port can be relevant to remove if the sum of unloaded and loaded containers is less than 25 % of the ships capacity.
		if(activity < 0.25 * vesselClass.getCapacity()){
			return true;
		}
		return false;
	}
	
	public void implementRemoveWorstPort(Edge bestDwellEdge){
		int prevNoInRot = bestDwellEdge.getPrevEdge().getNoInRotation();
		Edge bestRealDwell = null;
		for(Edge e : rotationEdges){
			if(e.getNoInRotation()== prevNoInRot){
				bestRealDwell = e.getNextEdge();
				if(!bestRealDwell.isDwell()){
					throw new RuntimeException("Input mismatch. Edge found was not dwell");
				}
				break;
			}
		}
		rotationGraph.removePort(bestDwellEdge);
		mainGraph.removePort(bestRealDwell);
	}

	private boolean enoughVessels() {
		int lbNoVessels = calculateMinNoVessels();
//		System.out.println("lb: " + lbNoVessels + " available: " + mainGraph.getNoVesselsAvailable(vesselClass.getId()));
		if(lbNoVessels < mainGraph.getNoVesselsAvailable(vesselClass.getId())){
			return true;
		}
		return false;
	}
	
	public void calcOptimalSpeed(){
		int lowestCost = Integer.MAX_VALUE;
		int lbNoVessels = calculateMinNoVessels();
		int ubNoVessels = calculateMaxNoVessels();
//		System.out.println("noAvailable: " + mainGraph.getNoVesselsAvailable(vesselClass.getId()) + " lb: " + lbNoVessels + " ub: " + ubNoVessels);
		if(lbNoVessels > ubNoVessels){
			this.speed = vesselClass.getMinSpeed();
			setNoOfVessels(lbNoVessels);
			setSailTimes();
			setDwellTimes();

		} else {
			for(int i = lbNoVessels; i <= ubNoVessels; i++){
				double speed = calculateSpeed(i);
				int bunkerCost = calcSailingBunkerCost(speed, i);
				int TCRate = i * vesselClass.getTCRate();
				int cost = bunkerCost + TCRate;
				if(cost < lowestCost && i <= mainGraph.getNoVesselsAvailable(vesselClass.getId())){
					lowestCost = cost;
					this.speed = speed;
					setNoOfVessels(i);
				}
			}
			setSailTimes();
			setDwellTimes();
		}
	}

	private void setNoOfVessels(int newNoOfVessels){
		mainGraph.removeNoUsed(vesselClass, noOfVessels);
		noOfVessels = newNoOfVessels;
		mainGraph.addNoUsed(vesselClass, newNoOfVessels);
	}

	private void setSailTimes() {
		for(Edge e : rotationEdges){
			if(e.isSail() && e.isActive()){
				e.setTravelTime(e.getDistance().getDistance()/this.speed);	
			}
		}
	}

	private void setDwellTimes() {
		double travelTime = 0;
		int numDwells = 0;
		for(Edge e : rotationEdges){
			if(e.isDwell() && e.isActive()){
				e.setTravelTime(Data.getPortStay());
				numDwells++;
			}
			if(e.isActive()){
				travelTime += e.getTravelTime();
			}
		}
		double diffFromWeek = 168.0 * noOfVessels - travelTime;
		if(diffFromWeek < 0 - Graph.DOUBLE_TOLERANCE){
			throw new RuntimeException("invalid dwell times. DiffFromWeek: " + diffFromWeek);
		}
		double extraDwellTime = diffFromWeek / numDwells;
		for(Edge e : rotationEdges){
			if(e.isDwell() && e.isActive()){
				e.setTravelTime(e.getTravelTime()+extraDwellTime);
			}
		}
	}

	public double calculateSpeed(int noOfVessels){
		double availableTime = 168 * noOfVessels - Data.getPortStay() * getNoOfPortStays();
		return distance / availableTime;
	}

	public int calculateMinNoVessels(){
		double rotationTime = (Data.getPortStay() * getNoOfPortStays() + (distance / vesselClass.getMaxSpeed())) / 168.0;
		int noVessels = (int) Math.ceil(rotationTime);
		return noVessels;
	}

	public int calculateMaxNoVessels(){
		double rotationTime = (Data.getPortStay() * getNoOfPortStays() + (distance / vesselClass.getMinSpeed())) / 168.0;
		int noVessels = (int) Math.floor(rotationTime);
		return noVessels;
	}

	public int getDistance(){
		return distance;
	}

	public VesselClass getVesselClass() {
		return vesselClass;
	}

	public void addRotationNode(Node node){
		rotationNodes.add(node);
	}

	public void addRotationEdge(Edge edge){
		if(edge.isSail()){
			int index = edge.getNoInRotation();
			rotationEdges.add(index, edge);
			distance += edge.getDistance().getDistance();
		} else if(edge.isDwell()) {
			rotationEdges.add(edge);
			Port port = edge.getFromNode().getPort();
			port.addDwellEdge(edge);
		}
	}

	public ArrayList<Node> getRotationNodes() {
		return rotationNodes;
	}

	public ArrayList<Edge> getRotationEdges() {
		return rotationEdges;
	}

	public int calcCost(){
		int obj = 0;
		VesselClass v = this.getVesselClass();
		ArrayList<Edge> rotationEdges = this.getRotationEdges();
		double sailingTime = 0;
		double idleTime = 0;
		int portCost = 0;
		int suezCost = 0;
		int panamaCost = 0;
		for (Edge e : rotationEdges){
			if(e.isSail() && e.isActive()){
				sailingTime += e.getTravelTime();
				Port p = e.getToNode().getPort();
				portCost += p.getFixedCallCost() + p.getVarCallCost() * v.getCapacity();
				if(e.isSuez()){
					suezCost += v.getSuezFee();
				}
				if(e.isPanama()){
					panamaCost += v.getPanamaFee();
				}
			}
			if(e.isDwell() && e.isActive()){
				idleTime += e.getTravelTime();
			}
		}
		int sailingBunkerCost = calcSailingBunkerCost(speed, noOfVessels);
		double idleBunkerCost = (int) Math.ceil(idleTime/24.0) * v.getFuelConsumptionIdle() * Data.getFuelPrice();

		int rotationDays = (int) Math.ceil((sailingTime+idleTime)/24.0);
		int TCCost = rotationDays * v.getTCRate();
		//		System.out.println("Rotation number "+ this.id);
		//		System.out.println("Voyage duration in nautical miles " + distance);
		//		System.out.println(this.noOfVessels + " ships needed sailing with speed " + speed);
		//		System.out.println("Port call cost " + portCost);
		//		System.out.println("Bunker idle burn in Ton " + idleBunkerCost/(double)Data.getFuelPrice());
		//		System.out.println("Bunker fuel burn in Ton " + sailingBunkerCost/(double)Data.getFuelPrice());
		//		System.out.println("Total TC cost " + TCCost);
		//		System.out.println();
		obj += sailingBunkerCost + idleBunkerCost + portCost + suezCost + panamaCost + TCCost;

		return obj;
	}

	public int calcPortCost(){
		int portCost = 0;
		for(Edge e : rotationEdges){
			if(e.isSail()){
				Port p = e.getToNode().getPort();
				portCost += p.getFixedCallCost() + vesselClass.getCapacity() * p.getVarCallCost();
			}
		}

		return portCost;
	}

	public int calcIdleFuelCost(){
		int idleTime = 0;
		for(Edge e : rotationEdges){
			if(e.isDwell()){
				idleTime += e.getTravelTime();
			}
		}
		int idleCost = (int) (Math.ceil(idleTime/24.0) * vesselClass.getFuelConsumptionIdle() * Data.getFuelPrice());

		return idleCost;
	}

	public int calcSailingBunkerCost(double speed, int noOfVessels){
		double fuelConsumption = vesselClass.getFuelConsumption(speed);
		double sailTimeDays = (distance / speed) / 24.0;
		double bunkerConsumption = sailTimeDays * fuelConsumption;
		return (int) (bunkerConsumption * Data.getFuelPrice());
	}

	public int getNoOfVessels() {
		return noOfVessels;
	}

	public int getId(){
		return id;
	}

	public double getSailTime(){
		double sailTime = 0;
		for(Edge e : rotationEdges){
			if(e.isSail()){
				sailTime += e.getTravelTime();
			}
		}

		return sailTime;
	}

	public ArrayList<Port> getPorts(){
		ArrayList<Port> ports = new ArrayList<Port>();
		for(Edge e : rotationEdges){
			if(e.getToNode().isArrival()){
				ports.add(e.getToNode().getPort());
			}
		}
		return ports;
	}

	public int getNoOfPortStays(){
		int counter = 0;
		for(Edge e : rotationEdges){
			if(e.isDwell() && e.isActive()){
				counter++;
			}
		}
		return counter;
	}

	/**
	 * @return active or not
	 */
	public boolean isActive() {
		return active;
	}


	/**
	 * Set rotation to active
	 */
	public void setActive() {
		this.active = true;
	}


	/**
	 * Set rotation to inactive
	 */
	public void setInactive(){
		this.active = false;
	}


	/**
	 * @param id the id to set
	 */
	public void setId(int id) {
		this.id = id;
	}

	@Override
	public String toString() {
		String print = "Rotation [vesselClass=" + vesselClass.getName() + ", noOfVessels=" + noOfVessels + "]\n";
		int counter = 0;
		for(Node i : rotationNodes){
			if(i.isDeparture()){
				print += "Port no. " + counter + ": " + i.getPort() + "\n";
				counter++;
			}
		}
		return print;
	}

	public void incrementNoInRotation(int fromNo) {
		for(Edge e : rotationEdges){
			if(e.getNoInRotation() > fromNo){
				e.incrementNoInRotation();
			}
		}
	}

	public void decrementNoInRotation(int fromNo) {
		for(Edge e : rotationEdges){
			if(e.getNoInRotation() > fromNo){
				e.decrementNoInRotation();
			}
		}
	}

	public void subtractDistance(int subtractDistance) {
		distance -= subtractDistance;
	}

	public void addDistance(int addDistance) {
		distance += addDistance;
	}

	public void removePort(int noInRotationIn, int noInRotationOut){
		if(noInRotationIn != noInRotationOut - 1 && noInRotationOut != 0){
			throw new RuntimeException("Input mismatch");
		}
		Edge ingoingEdge = rotationEdges.get(noInRotationIn);
		Edge dwell = ingoingEdge.getNextEdge();
		mainGraph.removePort(dwell);
	}

	public void insertPort(int noInRotation, Port p){
		Edge edge = rotationEdges.get(noInRotation);
		if(!edge.isSail()){
			throw new RuntimeException("Wrong input");
		}
		mainGraph.insertPort(this, edge, p);
	}

	public void delete(){
		if(!rotationNodes.isEmpty() || !rotationEdges.isEmpty()){
			throw new RuntimeException("Nodes and edges must be deleted first via Graph class.");
		}
		setNoOfVessels(0);
		distance = 0;
		setInactive();
	}

	public void serviceOmissionDemand(ArrayList<Demand> bestDemands, Rotation bestRotation, 
			int bestNoInRotation, int bestObj) {
		rotationGraph.serviceOmissionDemand(bestDemands, bestRotation, bestNoInRotation, bestObj);
	}
	
}
