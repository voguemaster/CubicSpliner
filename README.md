# CubicSpliner
Cusp is a simple 2D spline editing tool for free-form bezier splines or curves calculated by cubic spline interpolation.
Its main usage is to create 2D motion paths for simple games.

Splines can be created in free-form bezier mode or by creating control points and having cubic spline interpolation run on them
to create a smooth spline through all points.

Splines can be exported as a series of control points or can be sampled and converted to a series of lines (basically a polyline)
for use as a simple path in a simple game engine. This works better than using a spline solver for simple motion animation.


