package net.mcforge.world;


public class BlockUpdate {
    private int x;
    private int y;
    private int z;
    private Block b;
    public BlockUpdate(Block b, int x, int y, int z) { this.setBlock(b); this.setX(x); this.setY(y); this.setZ(z); }
    /**
     * @return the x
     */
    public int getX() {
        return x;
    }
    /**
     * @param x the x to set
     */
    public void setX(int x) {
        this.x = x;
    }
    /**
     * @return the y
     */
    public int getY() {
        return y;
    }
    /**
     * @param y the y to set
     */
    public void setY(int y) {
        this.y = y;
    }
    /**
     * @return the z
     */
    public int getZ() {
        return z;
    }
    /**
     * @param z the z to set
     */
    public void setZ(int z) {
        this.z = z;
    }
    /**
     * @return the b
     */
    public Block getBlock() {
        return b;
    }
    /**
     * @param b the b to set
     */
    public void setBlock(Block b) {
        this.b = b;
    }
}
