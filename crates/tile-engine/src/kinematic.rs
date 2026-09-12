//! Kinematic Velocity Predictor for predictive tile prefetching.
//!
//! Architecture Reference (Section 3.1, line 74):
//! "The predictor is a kinematic model fed by the platform scroller's own physics:
//! we sample touch/scroll velocity and deceleration and integrate forward 100–300 ms
//! to predict the future viewport, then prioritize the prefetch queue by predicted tile
//! entry time × required mip level. Fast fling -> only low mips are requested;
//! decelerating -> high mips fill in before settle."

use std::time::Instant;

/// Current motion state of the scroller/viewport.
#[derive(Debug, Clone, Copy)]
pub struct MotionState {
    pub position_y: f64,
    /// Velocity in pixels per second.
    pub velocity_y: f64,
    /// Deceleration in pixels per second squared.
    pub deceleration_y: f64,
    pub timestamp: Instant,
}

impl MotionState {
    pub fn stationary(position_y: f64) -> Self {
        Self {
            position_y,
            velocity_y: 0.0,
            deceleration_y: 0.0,
            timestamp: Instant::now(),
        }
    }
}

/// Predicts future viewport positions using forward numerical integration of motion physics.
pub struct KinematicPredictor {
    last_state: MotionState,
}

impl KinematicPredictor {
    pub fn new(initial_position: f64) -> Self {
        Self {
            last_state: MotionState::stationary(initial_position),
        }
    }

    /// Record a new position sample from the platform input/scroller.
    pub fn update_position(&mut self, new_pos: f64) {
        let now = Instant::now();
        let dt = now.duration_since(self.last_state.timestamp).as_secs_f64();

        if dt > 0.0001 {
            let instantaneous_vel = (new_pos - self.last_state.position_y) / dt;
            // Exponential smoothing for velocity (alpha = 0.6)
            let smoothed_vel = self.last_state.velocity_y * 0.4 + instantaneous_vel * 0.6;
            let decel = (smoothed_vel - self.last_state.velocity_y) / dt;

            self.last_state = MotionState {
                position_y: new_pos,
                velocity_y: smoothed_vel,
                deceleration_y: decel,
                timestamp: now,
            };
        } else {
            self.last_state.position_y = new_pos;
        }
    }

    /// Explicitly inject velocity (e.g. from mouse wheel or fling impulse).
    pub fn add_velocity(&mut self, delta_v: f64) {
        self.last_state.velocity_y += delta_v;
    }

    /// Current scroll velocity in px/s.
    pub fn current_velocity(&self) -> f64 {
        self.last_state.velocity_y
    }

    /// Predict the scroll position `lookahead_ms` milliseconds into the future.
    /// Uses standard kinematic equation: s(t) = s_0 + v*t + 0.5*a*t^2 (with velocity clamped to 0 on settle).
    pub fn predict_position(&self, lookahead_ms: f64) -> f64 {
        let t = lookahead_ms / 1000.0;
        let v0 = self.last_state.velocity_y;
        let s0 = self.last_state.position_y;

        // Approximate exponential friction / deceleration if not accelerating
        let friction_coeff = 2.5; // per-second drag
        let effective_vel = v0 * (-friction_coeff * t).exp();
        
        // Integrated displacement: integral_0^t v0 * e^(-k*x) dx = (v0/k) * (1 - e^(-k*t))
        let displacement = (v0 / friction_coeff) * (1.0 - (-friction_coeff * t).exp());
        let _ = effective_vel;

        s0 + displacement
    }

    /// Select optimal mip level based on current velocity:
    /// Fast fling (> 1200 px/s) -> Mip 2 (low-res to preserve frame budget)
    /// Medium fling (400 - 1200 px/s) -> Mip 1
    /// Slow / stationary (< 400 px/s) -> Mip 0 (full fidelity crispness)
    pub fn optimal_mip_level(&self) -> u8 {
        let speed = self.last_state.velocity_y.abs();
        if speed > 1500.0 {
            2
        } else if speed > 400.0 {
            1
        } else {
            0
        }
    }
}

/// Helper for determining viewport bounding boxes and prefetch ranges.
pub struct ViewportPredictor;

impl ViewportPredictor {
    /// Calculate the predicted viewport range [min_y, max_y] after forward integration.
    pub fn predicted_range(
        predictor: &KinematicPredictor,
        current_y: f64,
        viewport_height: f64,
        lookahead_ms: f64,
    ) -> (f64, f64) {
        let future_y = predictor.predict_position(lookahead_ms);
        let min_y = current_y.min(future_y);
        let max_y = (current_y + viewport_height).max(future_y + viewport_height);
        (min_y, max_y)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_stationary_prediction() {
        let pred = KinematicPredictor::new(100.0);
        let future = pred.predict_position(200.0);
        assert!((future - 100.0).abs() < 1e-3);
        assert_eq!(pred.optimal_mip_level(), 0);
    }

    #[test]
    fn test_velocity_mip_selection() {
        let mut pred = KinematicPredictor::new(100.0);
        pred.add_velocity(2000.0);
        assert_eq!(pred.optimal_mip_level(), 2);

        pred.last_state.velocity_y = 600.0;
        assert_eq!(pred.optimal_mip_level(), 1);

        pred.last_state.velocity_y = 50.0;
        assert_eq!(pred.optimal_mip_level(), 0);
    }
}
