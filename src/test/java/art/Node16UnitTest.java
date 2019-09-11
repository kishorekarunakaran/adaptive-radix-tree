package art;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.primitives.Bytes;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class Node16UnitTest {
	static Node16 createNode16() {
		return new Node16(createNode4());
	}

	static private Node4 createNode4() {
		Node4 node4 = new Node4();
		node4.addChild((byte) 1, Mockito.mock(AbstractNode.class));
		node4.addChild((byte) 2, Mockito.mock(AbstractNode.class));
		node4.addChild((byte) -2, Mockito.mock(AbstractNode.class));
		node4.addChild((byte) -1, Mockito.mock(AbstractNode.class));
		return node4;
	}

	@Test
	public void testUnderCapacityCreation() {
		Node4 node4 = new Node4();
		Assertions.assertThrows(IllegalArgumentException.class, () -> new Node16(node4));
	}

	@Test
	public void testGrowingInto() {
		// lexicographic sorted order
		byte partialKeys[] = new byte[] {1, 2, -2, -1};
		byte storedKeys[] = new byte[Node4.NODE_SIZE];
		Node4 node4 = new Node4();
		Node children[] = new Node[Node4.NODE_SIZE];
		for (int i = 0; i < Node4.NODE_SIZE; i++) {
			children[i] = Mockito.mock(AbstractNode.class);
			node4.addChild(partialKeys[i], children[i]);
			storedKeys[i] = BinaryComparableUtils.unsigned(partialKeys[i]);
		}

		Node16 node16 = new Node16(node4);
		assertEquals(Node4.NODE_SIZE, node16.noOfChildren);
		for (int i = 0; i < Node4.NODE_SIZE; i++) {
			assertEquals(storedKeys[i], node16.getKeys()[i]);
			assertEquals(children[i], node16.getChild()[i]);
		}
	}

	@Test
	public void testAddOnePartialKey() {
		byte partialKey = 3;
		byte storedKey = BinaryComparableUtils.unsigned(partialKey);

		Node16 node16 = createNode16();
		AbstractNode child = Mockito.spy(AbstractNode.class);
		assertTrue(node16.addChild(partialKey, child));
		assertEquals(5, node16.noOfChildren);
		assertEquals(child, node16.findChild(partialKey));

		// assert up links
		assertEquals(node16, child.parent());
		assertEquals(partialKey, child.uplinkKey());

		// assert inserted at correct position
		assertEquals(storedKey, node16.getKeys()[2]);
		assertEquals(child, node16.getChild()[2]);
	}

	@Test
	public void testFindForNonExistentPartialKey() {
		Node16 node16 = createNode16();
		assertNull(node16.findChild((byte) 3));
		assertNull(node16.findChild((byte) -3));
	}

	@Test
	public void testAddingTheSamePartialKeyAgain() {
		Node16 node16 = createNode16();
		Node child = Mockito.mock(AbstractNode.class);
		try {
			node16.addChild((byte) 1, child);
			fail();
		}
		catch (IllegalArgumentException e) {
		}

		try {
			node16.addChild((byte) -1, child);
			fail();
		}
		catch (IllegalArgumentException e) {
		}
	}

	@Test
	public void testAddTillCapacity() {
		Node16 node16 = createNode16();
		Node child = Mockito.mock(AbstractNode.class);

		// add till capacity
		for (int i = Node4.NODE_SIZE / 2 + 1; i <= Node16.NODE_SIZE / 2; i++) {
			assertTrue(node16.addChild((byte) i, child));
			assertTrue(node16.addChild((byte) -i, child));
		}
		assertEquals(Node16.NODE_SIZE, node16.noOfChildren);

		// noOfChildren 16 reached, now adds will fail
		assertFalse(node16.addChild((byte) 9, child));
		assertFalse(node16.addChild((byte) -9, child));

	}

	private Map<Byte, Node> getChildren(Node16 node16) {
		Map<Byte, Node> children = new HashMap<>();
		for (int i = 0; i < node16.noOfChildren; i++) {
			byte partialKey = node16.getKeys()[i];
			children.put(BinaryComparableUtils.signed(partialKey), node16.getChild()[i]);
		}
		return children;
	}

	private Map<Byte, Node> fillNode16(Node16 node16) {
		Map<Byte, Node> children = getChildren(node16);

		// add children upto remaining capacity
		// 3 to 8
		for (byte i = Node4.NODE_SIZE / 2 + 1; i <= Node16.NODE_SIZE / 2; i++) {
			Node child = Mockito.mock(AbstractNode.class);
			children.put(i, child);
			assertTrue(node16.addChild(i, child));

			child = Mockito.mock(AbstractNode.class);
			children.put((byte) -i, child);
			assertTrue(node16.addChild((byte) -i, child));
		}
		return children;
	}

	@Test
	public void testGrow() {
		Node16 node16 = createNode16();
		Map<Byte, Node> children = fillNode16(node16);

		assertTrue(node16.isFull());
		Node node = node16.grow();
		// assert we grow into next larger node type 48
		assertTrue(node instanceof Node48);
		Node48 node48 = (Node48) node;

		// assert all partial key mappings exist
		assertEquals(Node16.NODE_SIZE, node48.noOfChildren);
		for (byte i = 1; i <= Node16.NODE_SIZE / 2; i++) {
			assertEquals(children.get(i), node48.findChild(i));
			assertEquals(children.get((byte) -i), node48.findChild((byte) -i));

			// a bit of internal testing for Node48 here, that
			// it's keyIndex is correctly set
			assertNotEquals(Node48.ABSENT, node48.getKeyIndex()[i]);
			assertNotEquals(Node48.ABSENT, node48.getKeyIndex()[Byte.toUnsignedInt(i)]);

			// Node48's ctor copies the children into first 16 free slots
			// which for a newly created Node48 would be the first 16 itself.
			// That's what we assert here.
			// 1 to 8, -8 ... -1
			assertEquals(children.get(i), node48.getChild()[i - 1]);
			assertEquals(children.get((byte) -i), node48.getChild()[Node16.NODE_SIZE - i]);
		}
	}

	@Test
	public void testGrowBeforeFull() {
		Node16 node16 = createNode16();
		Assertions.assertThrows(IllegalStateException.class, node16::grow);
	}

	@Test
	public void testReplace() {
		Node16 node16 = createNode16();

		byte partialKey = 1;
		AbstractNode oldChild = (AbstractNode) node16.findChild(partialKey);
		AbstractNode newChild = Mockito.spy(AbstractNode.class);
		node16.replace(partialKey, newChild);
		assertEquals(newChild, node16.findChild(partialKey));

		// assert up links
		assertEquals(node16, newChild.parent());
		assertEquals(partialKey, newChild.uplinkKey());
		assertNull(oldChild.parent());

		partialKey = -1;
		oldChild = (AbstractNode) node16.findChild(partialKey);
		newChild = Mockito.spy(AbstractNode.class);
		node16.replace(partialKey, newChild);
		assertEquals(newChild, node16.findChild(partialKey));

		assertEquals(node16, newChild.parent());
		assertEquals(partialKey, newChild.uplinkKey());
		assertNull(oldChild.parent());
	}

	@Test
	public void testReplaceForNonExistentPartialKey() {
		Node16 node16 = createNode16();
		Node child5 = Mockito.mock(AbstractNode.class);
		Assertions.assertThrows(IllegalArgumentException.class, () -> node16.replace((byte) 5, child5));
	}

	// TODO: add a testSubsequentAddChildCallsMaintainOrder_Middle test

	@Test
	public void testSubsequentAddChildCallsMaintainOrder_Descending() {
		Node4 node4 = new Node4();

		// partialKeys in lexicographic descending order
		byte partialKeys[] = new byte[Node16.NODE_SIZE];
		byte storedKeys[] = new byte[Node16.NODE_SIZE];
		Node children[] = new Node[Node16.NODE_SIZE];
		for (int i = 1; i <= Node16.NODE_SIZE / 2; i++) {
			partialKeys[i - 1] = (byte) -i;
			storedKeys[i - 1] = BinaryComparableUtils.unsigned(partialKeys[i - 1]);
			children[i - 1] = Mockito.mock(AbstractNode.class);
			// insert first 4 in lexicographic order (-1, -2, -3, -4)
			if (i <= 4) {
				node4.addChild(partialKeys[i - 1], children[i - 1]);
			}

			partialKeys[i + 7] = (byte) (9 - i);
			storedKeys[i + 7] = BinaryComparableUtils.unsigned(partialKeys[i + 7]);
			children[i + 7] = Mockito.mock(AbstractNode.class);
		}

		Node16 node16 = new Node16(node4);

		// test those right shifts to create space for the next smaller element to be added
		for (int i = Node4.NODE_SIZE; i < Node16.NODE_SIZE; i++) {
			// add ith partial key
			node16.addChild(partialKeys[i], children[i]);

			List<Byte> reversedStoredKeys = Lists.newArrayList((Bytes.asList(storedKeys).subList(0, i + 1)));
			Collections.reverse(reversedStoredKeys);
			List<Node> reversedChildren = Lists.newArrayList((Arrays.asList(children).subList(0, i + 1)));
			Collections.reverse(reversedChildren);
			for (int j = 0; j <= i; j++) {
				byte key = node16.getKeys()[j];
				Node child = node16.getChild()[j];
				// System.out.println(BinaryComparableUtils.signed(reversedStoredKeys.get(j)));
				// System.out.println(BinaryComparableUtils.signed(key));
				assertEquals((byte) reversedStoredKeys.get(j), key);
				assertEquals(reversedChildren.get(j), child);
			}
		}
	}

	@Test
	public void testSubsequentAddChildCallsMaintainOrder_Ascending() {
		Node4 node4 = new Node4();

		// partialKeys in lexicographic ascending order
		byte partialKeys[] = new byte[Node16.NODE_SIZE];
		byte storedKeys[] = new byte[Node16.NODE_SIZE];
		Node children[] = new Node[Node16.NODE_SIZE];
		for (int i = 1; i <= Node16.NODE_SIZE / 2; i++) {
			partialKeys[i - 1] = (byte) i;
			storedKeys[i - 1] = BinaryComparableUtils.unsigned(partialKeys[i - 1]);
			children[i - 1] = Mockito.mock(AbstractNode.class);
			// insert first 4 in lexicographic order (-1, -2, -3, -4)
			if (i <= 4) {
				node4.addChild(partialKeys[i - 1], children[i - 1]);
			}

			partialKeys[i + 7] = (byte) -(9 - i);
			storedKeys[i + 7] = BinaryComparableUtils.unsigned(partialKeys[i + 7]);
			children[i + 7] = Mockito.mock(AbstractNode.class);
		}

		Node16 node16 = new Node16(node4);

		// test those right shifts to create space for the next smaller element to be added
		for (int i = Node4.NODE_SIZE; i < Node16.NODE_SIZE; i++) {
			// add ith partial key
			node16.addChild(partialKeys[i], children[i]);

			for (int j = 0; j <= i; j++) {
				byte key = node16.getKeys()[j];
				Node child = node16.getChild()[j];
				assertEquals(storedKeys[j], key);
				assertEquals(children[j], child);
			}
		}
	}

	@Test
	public void testRemove() {
		Node16 node16 = createNode16();
		AbstractNode child1 = Mockito.mock(AbstractNode.class);
		AbstractNode child2 = Mockito.mock(AbstractNode.class);

		byte partialKey1 = 3;
		byte partialKey2 = -3;
		node16.addChild(partialKey1, child1);
		node16.addChild(partialKey2, child2);

		assertEquals(6, node16.noOfChildren);
		assertEquals(child1, node16.findChild(partialKey1));
		node16.removeChild(partialKey1);
		assertNull(node16.findChild(partialKey1));
		assertEquals(5, node16.noOfChildren);

		assertEquals(child2, node16.findChild(partialKey2));
		node16.removeChild(partialKey2);
		assertNull(node16.findChild(partialKey2));
		assertEquals(4, node16.noOfChildren);

		assertNull(child1.parent());
		assertNull(child2.parent());
	}

	@Test
	public void testRemoveForNonExistentPartialKey() {
		Node16 node16 = createNode16();
		byte partialKey = 5;
		try {
			node16.removeChild(partialKey);
			fail();
		}
		catch (IllegalArgumentException e) {
		}
		partialKey = -5;
		try {
			node16.removeChild(partialKey);
			fail();
		}
		catch (IllegalArgumentException e) {
		}
	}

	// TODO: add a testRemoveMaintainsOrder_Middle test

	@Test
	public void testRemoveMaintainsOrder_Ascending() {
		Node4 node4 = new Node4();

		// partialKeys in lexicographic ascending order
		byte partialKeys[] = new byte[Node16.NODE_SIZE];
		byte storedKeys[] = new byte[Node16.NODE_SIZE];
		Node children[] = new Node[Node16.NODE_SIZE];
		for (int i = 1; i <= Node16.NODE_SIZE / 2; i++) {
			partialKeys[i - 1] = (byte) i;
			storedKeys[i - 1] = BinaryComparableUtils.unsigned(partialKeys[i - 1]);
			children[i - 1] = Mockito.mock(AbstractNode.class);
			// insert first 4 in lexicographic order (-1, -2, -3, -4)
			if (i <= 4) {
				node4.addChild(partialKeys[i - 1], children[i - 1]);
			}

			partialKeys[i + 7] = (byte) -(9 - i);
			storedKeys[i + 7] = BinaryComparableUtils.unsigned(partialKeys[i + 7]);
			children[i + 7] = Mockito.mock(AbstractNode.class);
		}

		Node16 node16 = new Node16(node4);

		for (int i = Node4.NODE_SIZE; i < Node16.NODE_SIZE; i++) {
			// add ith partial key
			node16.addChild(partialKeys[i], children[i]);
		}

		// remove one by one
		// test those left shifts to cover up the removed element
		for (int i = 0; i < Node16.NODE_SIZE; i++) {
			// remove ith partial key
			node16.removeChild(partialKeys[i]);

			for (int j = 0; j < node16.noOfChildren; j++) {
				byte key = node16.getKeys()[j];
				Node child = node16.getChild()[j];
				assertEquals(storedKeys[j + i + 1], key);
				assertEquals(children[j + i + 1], child);
			}
		}

		assertEquals(0, node16.noOfChildren);
	}

	@Test
	public void testRemoveMaintainsOrder_Descending() {
		Node4 node4 = new Node4();

		// partialKeys in lexicographic descending order
		byte partialKeys[] = new byte[Node16.NODE_SIZE];
		byte storedKeys[] = new byte[Node16.NODE_SIZE];
		Node children[] = new Node[Node16.NODE_SIZE];
		for (int i = 1; i <= Node16.NODE_SIZE / 2; i++) {
			partialKeys[i - 1] = (byte) -i;
			storedKeys[i - 1] = BinaryComparableUtils.unsigned(partialKeys[i - 1]);
			children[i - 1] = Mockito.mock(AbstractNode.class);
			// insert first 4 in lexicographic order (-1, -2, -3, -4)
			if (i <= 4) {
				node4.addChild(partialKeys[i - 1], children[i - 1]);
			}

			partialKeys[i + 7] = (byte) (9 - i);
			storedKeys[i + 7] = BinaryComparableUtils.unsigned(partialKeys[i + 7]);
			children[i + 7] = Mockito.mock(AbstractNode.class);
		}

		Node16 node16 = new Node16(node4);

		for (int i = Node4.NODE_SIZE; i < Node16.NODE_SIZE; i++) {
			// add ith partial key
			node16.addChild(partialKeys[i], children[i]);
		}

		// remove one by one
		// test those left shifts to cover up the removed element
		for (int i = 0; i < Node16.NODE_SIZE; i++) {
			// remove ith partial key
			node16.removeChild(partialKeys[i]);

			List<Byte> reversedStoredKeys = Lists
					.newArrayList((Bytes.asList(storedKeys).subList(i + 1, Node16.NODE_SIZE)));
			Collections.reverse(reversedStoredKeys);
			List<Node> reversedChildren = Lists
					.newArrayList((Arrays.asList(children).subList(i + 1, Node16.NODE_SIZE)));
			Collections.reverse(reversedChildren);
			for (int j = 0; j < node16.noOfChildren; j++) {
				byte key = node16.getKeys()[j];
				Node child = node16.getChild()[j];
				assertEquals((byte) reversedStoredKeys.get(j), key);
				assertEquals(reversedChildren.get(j), child);
			}
		}

		assertEquals(0, node16.noOfChildren);
	}
}
