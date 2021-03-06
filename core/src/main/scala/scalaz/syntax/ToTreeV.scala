package scalaz
package syntax

trait TreeV[A] extends SyntaxV[A] {
  def node(subForest: Tree[A]*): Tree[A] = Tree.node(self, subForest.toStream)

  def leaf: Tree[A] = Tree.leaf(self)
}

trait ToTreeV {
  implicit def ToTreeV[A](a: A) = new TreeV[A]{ def self = a }
}
