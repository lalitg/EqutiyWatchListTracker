import React, { useEffect } from 'react';
import './Toast.css';

const Toast = ({ message, visible, onHide }) => {
  useEffect(() => {
    if (!visible) return;
    const t = setTimeout(onHide, 3000);
    return () => clearTimeout(t);
  }, [visible, onHide]);

  if (!visible) return null;

  return (
    <div className="toast">
      {message}
    </div>
  );
};

export default Toast;
