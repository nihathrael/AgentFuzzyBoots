package client.agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

import server.Action;
import server.Directions;

public class AgentFuzzyBoots extends AbstractAgent {
	private static final int AcceptableDangerEstimate = 25;
	/**
	 * {@link AgentFuzzyBoots} is an agent with an internal model of the world
	 * and goals it tries to accomplish. Main goals: 1. Collect gold 2. Don't
	 * walk into Wumpus or pits 3. Explore 4. Shoot arrow when Wumpus location
	 * known and no other way is available 5. Return to level beginning
	 * 
	 * TODO Implement Arrow shooting 
	 */
	private int currentOrientation;
	private InternalCell currentCell;

	/**
	 * The agents view of the world
	 */
	private HashMap<Position, InternalCell> map = new HashMap<Position, InternalCell>();

	/**
	 * Current sequence the agent is using
	 */
	private List<Integer> sequence = null;

	private Goal[] goals = { new PickupGoldGoal(), new ExploreGoal(),
			new ReturnHomeGoal() };

	public AgentFuzzyBoots() {
		super();
		this.currentOrientation = this.startOrientation;
		InternalCell start = new InternalCell(new Position(this.startLocationX, this.startLocationY));
		this.currentCell = start;
		start.visited = true;
		map.put(currentCell.position, start);
		addSurroundingCells();
	}

	@Override
	protected int chooseAction(Percepts p) {
		updateCells(p);
		if (sequence == null || sequence.isEmpty()) {
			Goal goal = generateGoal();
			sequence = goal.generateActionSequence(this);
		}
		int action = sequence.remove(0);

		// Make sure we set the current rotation correctly
		if (action == Action.TURN_RIGHT) {
			currentOrientation = Directions.turnRight(currentOrientation);
		} else if (action == Action.TURN_LEFT) {
			currentOrientation = Directions.turnLeft(currentOrientation);
		}
		return action;
	}

	private Goal generateGoal() {
		for (Goal goal : goals) {
			if (goal.choosable(this))
				return goal;
		}
		throw new RuntimeException("Bad things are bad");
	}

	private void updateCells(Percepts p) {
		int x_coord = currentCell.position.x;
		int y_coord = currentCell.position.y;
		if (p.lastAction == Action.GOFORWARD) {
			switch (currentOrientation) {
			case Directions.EAST:
				++x_coord;
				break;
			case Directions.WEST:
				--x_coord;
				break;
			case Directions.SOUTH:
				y_coord--;
				break;
			case Directions.NORTH:
				y_coord++;
				break;
			}
		}
		InternalCell next = map.get(new Position(x_coord, y_coord));
		if (!p.bump) {
			currentCell = next;
			addSurroundingCells();
			next.loadPercept(p);
		} else {
			System.out.println("OUH Wall..");
			this.sequence = null;
			next.isWall = true;
		}
		next.visited = true;
		System.out.println("Now at: " + currentCell.position.x + ":"
				+ currentCell.position.y);
		// infer wumpus, pit
	}

	private void addSurroundingCells() {
		for (Position pos : Position.getSurroundingPositions(currentCell.position)) {
			if (map.get(pos) == null)
				map.put(pos, new InternalCell(pos));
		}
	}

	@Override
	public void resetAgent() {
	}

	public static List<Integer> getActionSequenceForPath(List<InternalCell> path,
			AgentFuzzyBoots agent) {
		ArrayList<Integer> ret = new ArrayList<Integer>();
		InternalCell last = agent.currentCell;
		int orientation = agent.currentOrientation;
		for (InternalCell cur : path) {
			if (cur.position.x < last.position.x) {
				for (int i = 0; i < Directions.getRequiredLeftTurns(
						orientation, Directions.WEST); ++i) {
					ret.add(Action.TURN_LEFT);
					orientation = Directions.WEST;
				}
			} else if (cur.position.x > last.position.x) {
				for (int i = 0; i < Directions.getRequiredRightTurns(
						orientation, Directions.EAST); ++i) {
					ret.add(Action.TURN_RIGHT);
					orientation = Directions.EAST;
				}
			} else if (cur.position.y < last.position.y) {
				for (int i = 0; i < Directions.getRequiredRightTurns(
						orientation, Directions.SOUTH); ++i) {
					ret.add(Action.TURN_RIGHT);
					orientation = Directions.SOUTH;
				}
			} else if (cur.position.y > last.position.y) {
				for (int i = 0; i < Directions.getRequiredLeftTurns(
						orientation, Directions.NORTH); ++i) {
					ret.add(Action.TURN_LEFT);
					orientation = Directions.NORTH;
				}
			}
			ret.add(Action.GOFORWARD);
			last = cur;
		}
		return ret;
	}

	private static class InternalCell {
		public final Position position;
		public boolean hasGold = false;
		public boolean hasStench = false;
		public boolean hasBreeze = false;
		public boolean isWall = false;
		public boolean visited = false;

		public InternalCell(Position pos) {
			position = pos;
		}

		/**
		 * Returns how dangerous this field is.
		 * 
		 * @return
		 */
		public int getDangerEstimate(AgentFuzzyBoots agent) {
			return (hasWumpus(agent) + hasPit(agent));
		}
		
		public String toString() {
			return "InternalCell[" + position.x + ":" + position.y + "]";
		}

		public int hasWumpus(AgentFuzzyBoots agent) {
			if(visited) return 0;
			int chance = 0;
			for (Position pos : Position.getSurroundingPositions(position)) {
				if (agent.map.get(pos) != null && agent.map.get(pos).hasStench) {
						chance += 25;
				}
			}
			System.out.println("Wumpus danger for " + this + ": " + chance);
			return chance;
		}

		public int hasPit(AgentFuzzyBoots agent) {
			if(visited) return 0;
			int chance = 0;
			for (Position pos : Position.getSurroundingPositions(position)) {
				if (agent.map.get(pos) != null && agent.map.get(pos).hasBreeze) {
						chance += 25;
				}
			}
			System.out.println("Pit    danger for " + this + ": " + chance);
			return chance;
		}

		public void loadPercept(Percepts perc) {
			hasStench = perc.stench;
			hasBreeze = perc.breeze;
			hasGold = perc.glitter;
		}
	}

	static class Position implements Comparable<Position> {

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + x;
			result = prime * result + y;
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
			Position other = (Position) obj;
			if (x != other.x)
				return false;
			if (y != other.y)
				return false;
			return true;
		}

		public final int x, y;

		public Position(int x, int y) {
			this.x = x;
			this.y = y;
		}
		
		public Integer distanceTo(Position other) {
			int x = this.x-other.x;
			int y = this.y-other.y;
			return x*x+y*y;
		}

		@Override
		public int compareTo(Position o) {
			return (o.x == this.x && o.y == this.y) ? 0 : 1;
		}

		public static ArrayList<AgentFuzzyBoots.Position> getSurroundingPositions(AgentFuzzyBoots.Position center) {
			ArrayList<AgentFuzzyBoots.Position> posis = new ArrayList<AgentFuzzyBoots.Position>(4);
			posis.add(new AgentFuzzyBoots.Position(center.x + 1, center.y));
			posis.add(new AgentFuzzyBoots.Position(center.x - 1, center.y));
			posis.add(new AgentFuzzyBoots.Position(center.x, center.y + 1));
			posis.add(new AgentFuzzyBoots.Position(center.x, center.y - 1));
			return posis;
		}
	}

	private static interface Goal {
		public boolean choosable(AgentFuzzyBoots agent);

		public List<Integer> generateActionSequence(AgentFuzzyBoots agent);
	}

	private static class ExploreGoal implements Goal {

		private List<InternalCell> targets;

		@Override
		public boolean choosable(final AgentFuzzyBoots agent) {
			// We can still try to find gold if we have a cell on our map
			// we haven't visited and that isn't too dangerous
			targets = new ArrayList<InternalCell>();
			targets.addAll(agent.map.values());
			for (Iterator<InternalCell> it = targets.iterator(); it.hasNext(); ) {
				InternalCell next = it.next();
		        if (next.visited || next.getDangerEstimate(agent) > AcceptableDangerEstimate) {
		        	it.remove();
		        }
			}
			Collections.sort(targets, new Comparator<InternalCell>() {
				@Override
				public int compare(InternalCell o1, InternalCell o2) {
					return agent.currentCell.position.distanceTo(o1.position).compareTo(
							agent.currentCell.position.distanceTo(o2.position));
				}
			});
			if (!targets.isEmpty()) {
				System.out.println("Found legal target(s). First: "
						+ targets.get(0).position.x + ":"
						+ targets.get(0).position.y);
				return true;
			} else {
				return false;
			}
		}

		@Override
		public List<Integer> generateActionSequence(AgentFuzzyBoots agent) {
			List<InternalCell> path = null;
			for (InternalCell nextTarget: targets) {
				path = calculateRoute(agent.currentCell,
						nextTarget, agent);
				if (path != null) break;
			}
			if (path == null) throw new RuntimeException("None of the targets was reachable!");
			return getActionSequenceForPath(path, agent);
		}

	}

	private static class ReturnHomeGoal implements Goal {

		@Override
		public boolean choosable(AgentFuzzyBoots agent) {
			return true;// always return true, we can always go home
		}

		@Override
		public List<Integer> generateActionSequence(AgentFuzzyBoots agent) {
			ArrayList<InternalCell> path = calculateRoute(agent.currentCell, agent.map
					.get(new Position(agent.startLocationX,
							agent.startLocationY)), agent);
			List<Integer> sequence = getActionSequenceForPath(path, agent);
			sequence.add(Action.CLIMB);
			return sequence;
		}
	}

	private static class PickupGoldGoal implements Goal {

		@Override
		public boolean choosable(AgentFuzzyBoots agent) {
			return agent.currentCell.hasGold;
		}

		@Override
		public ArrayList<Integer> generateActionSequence(AgentFuzzyBoots agent) {
			ArrayList<Integer> ret = new ArrayList<Integer>(1);
			ret.add(Action.GRAB);
			return ret;
		}
	}

	public static ArrayList<InternalCell> calculateRoute(InternalCell from,
			InternalCell to, AgentFuzzyBoots agent) {
		System.out.println("=========================");
		System.out.printf("Looking for path from %s to %s\n", from.toString(), to.toString());
		ArrayList<InternalCell> ret = new ArrayList<InternalCell>();
		final HashMap<InternalCell, Integer> distance = new HashMap<InternalCell, Integer>();
		HashMap<InternalCell, InternalCell> previous = new HashMap<InternalCell, InternalCell>();

		// Priority queue for dijkstra
		PriorityQueue<InternalCell> que = new PriorityQueue<InternalCell>(agent.map
				.size(), new Comparator<InternalCell>() {
			public int compare(InternalCell o1, InternalCell o2) {
				if (distance.get(o1) == null) 		return 1;
				else if (distance.get(o2) == null) 	return -1;
				else 								return distance.get(o1).compareTo(distance.get(o2));
			}
		});
		for (InternalCell entry : agent.map.values()) {
			if (entry.getDangerEstimate(agent) < AcceptableDangerEstimate
					&& !entry.isWall) {
				distance.put(entry, null);
				previous.put(entry, null);
				que.add(entry);
			}
		}
		que.remove(from);
		distance.put(from, 0);
		que.add(from);
		ArrayList<InternalCell> neighbors = new ArrayList<InternalCell>();
		InternalCell u = null;
		while (!que.isEmpty()) {
			u = que.peek();
			if (distance.get(u) == null)
				break;
			que.poll();
			neighbors.clear();
			for (InternalCell entry : que) {
				if (Math.abs(entry.position.x - u.position.x)
						+ Math.abs(entry.position.y - u.position.y) == 1) {
					neighbors.add(entry);
				}
			}
			for (InternalCell neigh : neighbors) {
				int alt = distance.get(u) + 1;
				if (distance.get(neigh) == null || alt < distance.get(neigh)) {
					que.remove(neigh);
					distance.put(neigh, alt);
					previous.put(neigh, u);
					que.add(neigh);
				}
			}
		}
		InternalCell tmp = to;
		while (tmp != from) {
			if (tmp == null) return null; // no way found
			ret.add(tmp);
			tmp = previous.get(tmp);
		}
		Collections.reverse(ret);
		System.out.printf("Moving from %d:%d to %d:%d distance: %d\n",
				from.position.x, from.position.y, to.position.x, to.position.y,
				distance.get(to));
		System.out.print("Path: ");
		for (InternalCell cell : ret) {
			System.out.printf("[%d:%d] -> ", cell.position.x, cell.position.y);
		}
		System.out.println("Done");
		System.out.println("=========================");
		return ret;
	}

}
