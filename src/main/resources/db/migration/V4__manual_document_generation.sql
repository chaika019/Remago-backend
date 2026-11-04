-- Manual document generation from the document workspace.
-- Specifications created directly from the Documents screen are not required
-- to have a CRM deal. CRM-generated specifications still keep deal_id.
ALTER TABLE specifications
    ALTER COLUMN deal_id DROP NOT NULL;
