

/**
 * A simple vector class that provides simple vector functionality
 */
public class Vector2D
{
    //////////////////////////////////////////////
    // members
    public float m_x, m_y;

    /**
     * Default ctor
     */
    public Vector2D()
    {
        m_x = m_y = 0.0f;
    }


    /**
     * Initialize with values
     * @param x
     * @param y
     */
    public Vector2D(float x, float y)
    {
        m_x = x;
        m_y = y;
    }


    public Vector2D(final Vector2D rhs)
    {
        m_x = rhs.m_x;
        m_y = rhs.m_y;
    }


    public void copy(final Vector2D rhs)
    {
        m_x = rhs.m_x;
        m_y = rhs.m_y;
    }


    /**
     * Set new values
     * @param x
     * @param y
     */
    public void set(float x, float y)
    {
        m_x = x;
        m_y = y;
    }


    /**
     * Add another vector
     * @param rhs
     */
    public void add(Vector2D rhs)
    {
        m_x += rhs.m_x;
        m_y += rhs.m_y;
    }


    /**
     * Substract a vector from this one
     * @param rhs
     */
    public void substract(Vector2D rhs)
    {
        m_x -= rhs.m_x;
        m_y -= rhs.m_y;
    }


    /**
     * Scale the vector
     * @param scalar
     */
    public void multiply(float scalar)
    {
        m_x *= scalar;
        m_y *= scalar;
    }


    /**
     * Reverse direction of the vector
     */
    public void negate()
    {
        m_x = -m_x;
        m_y = -m_y;
    }


    /**
     * Return the length of the vector, squared
     * @return The length, squared
     */
    public float getLengthSquared()
    {
        return m_x*m_x + m_y*m_y;
    }

    /**
     * Return the magnitude of this vector
     * @return The magnitude of this vector
     */
    public float getLength()
    {
        return (float)Math.sqrt(m_x*m_x + m_y*m_y);
    }


    /**
     * Normalize this vector
     */
    public void normalize()
    {
        float f = getLength();
        if (Math.abs(f) > 1e-03)
        {
            float r_f = 1.0f / f;
            m_x *= r_f;
            m_y *= r_f;
        }
    }


}
