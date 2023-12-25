export const capitalizeAndAddSpace = (text: string) => {
    if (typeof text !== 'string') return ''
    return text.charAt(0).toUpperCase() + text.slice(1).replace(/([A-Z])/g, ' $1').trim()
}