package client.agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import server.Action;
import server.Directions;

public class AgentFuzzyBoots extends AbstractAgent {
	private static final int AcceptableDangerEstimate = 25;
	/**
	 * {@link AgentFuzzyBoots} is an agent with an internal model of the world
	 * and goals it tries to accomplish. Main goals: 1. Collect gold 2. Don't
	 * walk into wumpus or pits 3. Explore 4. Shoot arrow when wumpus location
	 * known and no other way is available 5. Return to level beginning
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
		InternalCell start = new InternalCell(new Position(this.startLocationX,
				this.startLocationY));
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
		for (Position pos : getSurroundingPositions(currentCell.position)) {
			if (map.get(pos) == null)
				map.put(pos, new InternalCell(pos));
		}
	}

	public List<Position> getSurroundingPositions(Position center) {
		List<Position> posis = new ArrayList<Position>(4);
		posis.add(new Position(center.x + 1, center.y));
		posis.add(new Position(center.x - 1, center.y));
		posis.add(new Position(center.x, center.y + 1));
		posis.add(new Position(center.x, center.y - 1));
		return posis;
	}

	@Override
	public void resetAgent() {

	}

	private class InternalCell {
		public final Position position;
		public boolean hasGold = true;
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
		public int getDangerEstimate() {
			return (hasWumpus() + hasPit());
		}

		public int hasWumpus() {
			int chance = 0;
			for (Position pos : getSurroundingPositions(position)) {
				if (map.get(pos) != null) {
					if (map.get(pos).hasStench) {
						chance += 25;
					} else if (map.get(pos).visited) {
						return 0;
					}
				}
			}
			System.out.println("Wumpus danger: " + chance);
			return chance;
		}

		public int hasPit() {
			int chance = 0;
			for (Position pos : getSurroundingPositions(position)) {
				if (map.get(pos) != null) {
					if (map.get(pos).hasBreeze) {
						chance += 25;
					} else if (map.get(pos).visited) {
						return 0;
					}
				}
			}
			System.out.println("Pit danger: " + chance);
			return chance;
		}

		public void loadPercept(Percepts perc) {
			hasStench = perc.stench;
			hasBreeze = perc.breeze;
			hasGold = perc.glitter;
		}
	}

	private class Position implements Comparable<Position> {
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
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
			if (!getOuterType().equals(other.getOuterType()))
				return false;
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

		@Override
		public int compareTo(Position o) {
			return (o.x == this.x && o.y == this.y) ? 0 : 1;
		}

		private AgentFuzzyBoots getOuterType() {
			return AgentFuzzyBoots.this;
		}
	}

	private interface Goal {
		public boolean choosable(AgentFuzzyBoots agent);

		public List<Integer> generateActionSequence(AgentFuzzyBoots agent);
	}

	private class ExploreGoal implements Goal {

		private InternalCell nextTarget = null;

		@Override
		public boolean choosable(final AgentFuzzyBoots agent) {
			for (InternalCell cell : agent.map.values()) {
				// We can still try to find gold if we have a cell on our map
				// we haven't visited and that isn't too dangerous
				if (cell.visited == false
						&& cell.getDangerEstimate() < AcceptableDangerEstimate) {
					List<InternalCell> nodes = new ArrayList<InternalCell>();
					nodes.addAll(agent.map.values());
					nodes.remove(currentCell);
					nextTarget = Collections.min(nodes, new Comparator<InternalCell>() {
						@Override
						public int compare(InternalCell o1, InternalCell o2) {
							return distance(o1).compareTo(distance(o2));
						}
						
						private Integer distance(InternalCell cell) {
							int x = agent.currentCell.position.x - cell.position.x;
							int y = agent.currentCell.position.y - cell.position.y;
							return x*x + y*y;
						}
					});
					System.out.println("Found legal target: "
							+ nextTarget.position.x + ":"
							+ nextTarget.position.y);
					return true;
				}
			}
			return false;
		}

		@Override
		public List<Integer> generateActionSequence(AgentFuzzyBoots agent) {
			List<Integer> ret = new ArrayList<Integer>();
			List<InternalCell> path = calculateRoute(agent.currentCell,
					nextTarget, map);
			InternalCell last = agent.currentCell;
			for (InternalCell cur : path) {
				if (cur.position.x < last.position.x) {
					for (int i = 0; i < Directions.getRequiredLeftTurns(
							currentOrientation, Directions.WEST); ++i) {
						ret.add(Action.TURN_LEFT);
					}
				} else if (cur.position.x > last.position.x) {
					for (int i = 0; i < Directions.getRequiredRightTurns(
							currentOrientation, Directions.EAST); ++i) {
						ret.add(Action.TURN_RIGHT);
					}
				} else if (cur.position.y < last.position.y) {
					for (int i = 0; i < Directions.getRequiredRightTurns(
							currentOrientation, Directions.SOUTH); ++i) {
						ret.add(Action.TURN_RIGHT);
					}
				} else if (cur.position.y > last.position.y) {
					for (int i = 0; i < Directions.getRequiredLeftTurns(
							currentOrientation, Directions.NORTH); ++i) {
						ret.add(Action.TURN_LEFT);
					}
				}
				ret.add(Action.GOFORWARD);
				last = cur;
			}
			return ret;
		}

	}

	private class ReturnHomeGoal implements Goal {

		@Override
		public boolean choosable(AgentFuzzyBoots agent) {
			return true;// always return true, we can always go home
		}

		@Override
		public List<Integer> generateActionSequence(AgentFuzzyBoots agent) {
			List<Integer> ret = new ArrayList<Integer>();
			List<InternalCell> path = calculateRoute(currentCell, agent.map
					.get(new Position(agent.startLocationX,
							agent.startLocationY)), map);
			InternalCell last = agent.currentCell;
			for (InternalCell cur : path) {
				if (cur.position.x < last.position.x) {
					for (int i = 0; i < Directions.getRequiredLeftTurns(
							currentOrientation, Directions.WEST); ++i) {
						ret.add(Action.TURN_LEFT);
					}
				} else if (cur.position.x > last.position.x) {
					for (int i = 0; i < Directions.getRequiredRightTurns(
							currentOrientation, Directions.EAST); ++i) {
						ret.add(Action.TURN_RIGHT);
					}
				} else if (cur.position.y < last.position.y) {
					for (int i = 0; i < Directions.getRequiredRightTurns(
							currentOrientation, Directions.SOUTH); ++i) {
						ret.add(Action.TURN_RIGHT);
					}
				} else if (cur.position.y > last.position.y) {
					for (int i = 0; i < Directions.getRequiredLeftTurns(
							currentOrientation, Directions.NORTH); ++i) {
						ret.add(Action.TURN_LEFT);
					}
				}
				ret.add(Action.GOFORWARD);
				last = cur;
			}
			ret.add(Action.CLIMB);
			return ret;
		}
	}

	private class PickupGoldGoal implements Goal {

		@Override
		public boolean choosable(AgentFuzzyBoots agent) {
			return agent.currentCell.hasGold;
		}

		@Override
		public List<Integer> generateActionSequence(AgentFuzzyBoots agent) {
			List<Integer> ret = new ArrayList<Integer>(1);
			ret.add(Action.GRAB);
			return ret;
		}
	}

	public static List<InternalCell> calculateRoute(InternalCell from,
			InternalCell to, Map<Position, InternalCell> map) {
		System.out.println("=========================");
		System.out.println("Looking for path...");
		List<InternalCell> ret = new ArrayList<InternalCell>();
		final HashMap<InternalCell, Integer> distance = new HashMap<InternalCell, Integer>();
		HashMap<InternalCell, InternalCell> previous = new HashMap<InternalCell, InternalCell>();

		// Priority queue for dijkstra
		PriorityQueue<InternalCell> que = new PriorityQueue<InternalCell>(map
				.size(), new Comparator<InternalCell>() {
			public int compare(InternalCell o1, InternalCell o2) {
				if (distance.get(o1) == null)
					return new Integer(1).compareTo(0);
				else if (distance.get(o2) == null)
					return new Integer(0).compareTo(1);
				else
					return distance.get(o1).compareTo(distance.get(o2));
			}
		});
		for (InternalCell entry : map.values()) {
			if (entry.getDangerEstimate() < AcceptableDangerEstimate
					&& !entry.isWall) {
				distance.put(entry, null);
				previous.put(entry, null);
				que.add(entry);
			}
		}
		que.remove(from);
		distance.put(from, 0);
		que.add(from);
		while (!que.isEmpty()) {
			InternalCell u = que.peek();
			if (distance.get(u) == null)
				break;
			que.poll();
			List<InternalCell> neighbors = new ArrayList<InternalCell>();
			for (Map.Entry<Position, InternalCell> entry : map.entrySet()) {
				if (Math.abs(entry.getKey().x - u.position.x)
						+ Math.abs(entry.getKey().y - u.position.y) == 1) {
					neighbors.add(entry.getValue());
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
