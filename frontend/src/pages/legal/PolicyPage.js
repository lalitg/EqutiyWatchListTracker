import React from 'react';
import { PolicyDocument } from './LegalPage';

/**
 * Renders whichever policy document the route hands it.
 *
 * <p>This replaces six near-identical page components. They differed only in which prose they
 * held, which is now data in {@code ./content}, so six files were six chances to forget the
 * version stamp or the trailing navigation on the seventh.
 *
 * @param doc one of the documents exported by ./content
 */
const PolicyPage = ({ doc }) => <PolicyDocument doc={doc} />;

export default PolicyPage;
