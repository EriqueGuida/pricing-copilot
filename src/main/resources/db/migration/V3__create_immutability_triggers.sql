DELIMITER //

CREATE TRIGGER trg_price_history_no_update
BEFORE UPDATE ON price_history
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'price_history e imutavel: UPDATE proibido';
END//

CREATE TRIGGER trg_price_history_no_delete
BEFORE DELETE ON price_history
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'price_history e imutavel: DELETE proibido';
END//

CREATE TRIGGER trg_competitor_price_no_update
BEFORE UPDATE ON competitor_price
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'competitor_price e imutavel: UPDATE proibido';
END//

CREATE TRIGGER trg_competitor_price_no_delete
BEFORE DELETE ON competitor_price
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'competitor_price e imutavel: DELETE proibido';
END//

DELIMITER ;