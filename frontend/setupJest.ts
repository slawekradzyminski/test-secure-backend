import fetchMock from 'jest-fetch-mock';
import '@testing-library/jest-dom';

(global.fetch as any) = fetchMock;
fetchMock.enableMocks();