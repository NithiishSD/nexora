package com.nexora.shared.error;

// Thrown by entity methods when a status change is not allowed (DOMAIN_RULES §5). Maps to 409.
// Usage: throw new InvalidStateTransitionException("Party", status, PartyStatus.INACTIVE);
public class InvalidStateTransitionException extends BusinessException {

    public InvalidStateTransitionException(String entity, Object from, Object to) {
        super(ErrorCode.INVALID_STATE_TRANSITION,
                entity + " cannot change from " + from + " to " + to);
    }
}
