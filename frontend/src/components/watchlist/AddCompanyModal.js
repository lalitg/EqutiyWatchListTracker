import React, { useState } from 'react';
import Modal from '../shared/Modal';

const AddCompanyModal = ({ isOpen, onClose, onAdd, isLoading }) => {
  const [companyCode, setCompanyCode] = useState('');

  const handleSubmit = () => {
    const trimmed = companyCode.trim();
    if (!trimmed) return;
    onAdd(trimmed);
    setCompanyCode('');
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleSubmit();
  };

  const handleClose = () => {
    setCompanyCode('');
    onClose();
  };

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="Add Company">
      <label style={{ display: 'block', fontSize: 14, fontWeight: 600, color: '#374151', marginBottom: 8 }}>
        Company Code
      </label>
      <input
        type="text"
        value={companyCode}
        onChange={(e) => setCompanyCode(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Enter company code (e.g. INFY)"
        autoFocus
        disabled={isLoading}
        style={{
          width: '100%',
          padding: '14px 16px',
          fontSize: 15,
          border: '2px solid #e5e7eb',
          borderRadius: 10,
          outline: 'none',
          boxSizing: 'border-box',
          transition: 'all 0.3s ease',
        }}
      />
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 24 }}>
        <button className="btn btn-secondary" onClick={handleClose} disabled={isLoading}>
          Cancel
        </button>
        <button
          className="btn btn-primary"
          onClick={handleSubmit}
          disabled={isLoading || !companyCode.trim()}
        >
          {isLoading ? 'Adding...' : 'Add'}
        </button>
      </div>
    </Modal>
  );
};

export default AddCompanyModal;
