ALTER TABLE review_campaigns ADD COLUMN form_title VARCHAR(255);
ALTER TABLE review_campaigns ADD COLUMN form_schema TEXT;

UPDATE review_campaigns
SET form_title = (SELECT title FROM review_forms WHERE review_forms.id = review_campaigns.form_id),
    form_schema = (SELECT schema FROM review_forms WHERE review_forms.id = review_campaigns.form_id);

ALTER TABLE review_campaigns ALTER COLUMN form_title SET NOT NULL;
ALTER TABLE review_campaigns ALTER COLUMN form_schema SET NOT NULL;